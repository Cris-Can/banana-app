import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as crypto from "crypto";

const db = admin.firestore();

/**
 * =====================================================
 * 🧹 LIMPIEZA MASIVA DE BASE DE DATOS (Mantenimiento)
 * =====================================================
 * Recorre todos los perfiles de usuario y elimina campos
 * redundantes u obsoletos para optimizar Firestore.
 * Solo puede ser invocada por un Administrador.
 * =====================================================
 */
export const cleanupUsersDatabase = onCall(async (request) => {
  // 1. Verificación de Autenticación y Admin
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Debe iniciar sesión para realizar esta operación.");
  }

  const callerUid = request.auth.uid;
  const callerDoc = await db.collection("users").doc(callerUid).get();

  if (!callerDoc.exists || callerDoc.data()?.admin !== true) {
    throw new HttpsError("permission-denied", "Solo un administrador con privilegios puede ejecutar la limpieza.");
  }

  console.log(`[CLEANUP] Starting database cleanup by admin ${callerUid}...`);

  try {
    const usersSnap = await db.collection("users").get();
    const fieldsToDelete = [
      "gold", "goldStored", "premiumStored", "averageRating", 
      "ratingBadge", "ratingBadgeText", "perfectAttendee", 
      "canBoostFree", "founder", "isGoldStored", "premium"
    ];

    let batch = db.batch();
    let countInBatch = 0;
    let totalUpdated = 0;

    for (const doc of usersSnap.docs) {
      const data = doc.data();
      const updates: any = {};
      let hasSomethingToDelete = false;

      fieldsToDelete.forEach(field => {
        if (data[field] !== undefined) {
          updates[field] = admin.firestore.FieldValue.delete();
          hasSomethingToDelete = true;
        }
      });

      if (hasSomethingToDelete) {
        batch.update(doc.ref, updates);
        countInBatch++;
        totalUpdated++;
      }

      // Firestore batches have a limit of 500 operations
      if (countInBatch === 500) {
        await batch.commit();
        batch = db.batch();
        countInBatch = 0;
        console.log(`[CLEANUP] Committed a batch of 500 updates...`);
      }
    }

    // Commit any remaining updates
    if (countInBatch > 0) {
      await batch.commit();
    }

    console.log(`[CLEANUP] Successfully cleaned ${totalUpdated} user profiles.`);

    return {
      success: true,
      message: `Limpieza completada con éxito. Se actualizaron ${totalUpdated} usuarios de un total de ${usersSnap.size}.`,
      processedCount: usersSnap.size,
      updatedCount: totalUpdated
    };

  } catch (error: any) {
    console.error("[CLEANUP] Fatal error during database cleanup:", error);
    throw new HttpsError("internal", "Error durante el proceso de limpieza masiva.", error);
  }
});

/**
 * =====================================================
 * 🎟️ CREAR CÓDIGO PROMOCIONAL (Admin Only)
 * =====================================================
 */
export const createPromoCode = onCall(
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const adminUid = request.auth.uid;

    const adminDoc = await db.collection("users").doc(adminUid).get();
    if (!adminDoc.exists || adminDoc.data()?.admin !== true) {
      throw new HttpsError("permission-denied", "Privilegios insuficientes.");
    }

    const data = request.data || {};
    const type = (typeof data.type === "string" ? data.type.toUpperCase() : "FOUNDER");
    const maxUses = Math.max(1, parseInt(data.maxUses, 10) || 1);
    const durationDays = Math.max(0, parseInt(data.durationDays, 10) || 0);

    let expiresAt: admin.firestore.Timestamp | null = null;
    if (data.expiresAt && typeof data.expiresAt === "number") {
        const oneYearLimit = Date.now() + (365 * 24 * 60 * 60 * 1000);
        if (data.expiresAt > oneYearLimit) throw new HttpsError("invalid-argument", "Expiración máxima: 1 año.");
        if (data.expiresAt > Date.now()) expiresAt = admin.firestore.Timestamp.fromMillis(data.expiresAt);
    }

    // 🛡️ VALIDACIÓN DE CAMPAÑA (Integridad Referencial)
    const campaignId = (typeof data.campaignId === "string") ? data.campaignId.trim() : null;
    if (campaignId) {
        const campaignDoc = await db.collection("campaigns").doc(campaignId).get();
        if (!campaignDoc.exists) {
            throw new HttpsError("not-found", `La campaña '${campaignId}' no existe.`);
        }
    }

    const maxAttempts = 3;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        let finalCode = "";
        if (attempt === 1 && typeof data.code === "string" && data.code.trim() !== "") {
            finalCode = data.code.toUpperCase().replace(/[^A-Z0-9-]/g, '').trim();
        } else {
            const random = crypto.randomBytes(4).toString("hex").toUpperCase().substring(0, 6);
            finalCode = `${type === "FOUNDER" ? "FND" : "PRM"}-${random}`;
        }

        const inviteRef = db.collection("invitation_codes").doc(finalCode);
        try {
            const newPromoDoc: any = {
                type: type,
                durationDays: durationDays,
                maxUses: maxUses,
                usedCount: 0,
                usedByList: [],
                isActive: true,
                createdBy: adminUid,
                createdAt: admin.firestore.FieldValue.serverTimestamp()
            };
            if (campaignId) newPromoDoc.campaignId = campaignId;
            if (expiresAt) newPromoDoc.expiresAt = expiresAt;

            await inviteRef.create(newPromoDoc);
            return { success: true, code: finalCode, message: "Código creado." };
        } catch (error: any) {
            if (error.code === 6) { // ALREADY_EXISTS
                if (attempt === 1 && data.code) throw new HttpsError("already-exists", "Código ya en uso.");
                continue;
            }
            throw new HttpsError("internal", "Error al procesar el código.");
        }
    }
    throw new HttpsError("aborted", "No se pudo generar un código único.");
  }
);

/**
 * =====================================================
 * 📊 CREAR CAMPAÑA DE MARKETING (Admin Only)
 * =====================================================
 */
export const createCampaign = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const adminUid = request.auth.uid;

    const adminDoc = await db.collection("users").doc(adminUid).get();
    if (!adminDoc.exists || adminDoc.data()?.admin !== true) {
        throw new HttpsError("permission-denied", "Acceso denegado.");
    }

    const data = request.data || {};
    const name = typeof data.name === "string" ? data.name.trim() : "";
    if (!name) throw new HttpsError("invalid-argument", "El nombre es obligatorio.");

    const customId = typeof data.id === "string" ? data.id.trim() : null;
    const startAt = typeof data.startAt === "number" ? data.startAt : Date.now();
    const endAt = typeof data.endAt === "number" ? data.endAt : (startAt + 30 * 24 * 60 * 60 * 1000);

    const oneYearMs = 365 * 24 * 60 * 60 * 1000;
    if (endAt - startAt > oneYearMs) {
        throw new HttpsError("invalid-argument", "La duración máxima es de 1 año.");
    }

    const newCampaign = {
        name,
        description: (typeof data.description === "string") ? data.description.trim() : "",
        isActive: true,
        startAt: admin.firestore.Timestamp.fromMillis(startAt),
        endAt: admin.firestore.Timestamp.fromMillis(endAt),
        createdBy: adminUid,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    try {
        let finalId = "";
        if (customId) {
            await db.collection("campaigns").doc(customId).create(newCampaign);
            finalId = customId;
        } else {
            const docRef = await db.collection("campaigns").add(newCampaign);
            finalId = docRef.id;
        }
        return { success: true, id: finalId };
    } catch (error: any) {
        if (error.code === 6) throw new HttpsError("already-exists", "ID de campaña ya utilizado.");
        throw new HttpsError("internal", "Error al crear campaña.");
    }
});

/**
 * =====================================================
 * ⚙️ ACTIVAR/DESACTIVAR CAMPAÑA (Admin Only)
 * =====================================================
 */
export const toggleCampaignActive = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const adminUid = request.auth.uid;

    const adminDoc = await db.collection("users").doc(adminUid).get();
    if (!adminDoc.exists || adminDoc.data()?.admin !== true) {
        throw new HttpsError("permission-denied", "Acceso denegado.");
    }

    const { campaignId, isActive } = request.data;
    if (!campaignId || typeof isActive !== "boolean") {
        throw new HttpsError("invalid-argument", "campaignId e isActive son requeridos.");
    }

    const campaignRef = db.collection("campaigns").doc(campaignId);
    const campaignDoc = await campaignRef.get();
    if (!campaignDoc.exists) throw new HttpsError("not-found", "La campaña no existe.");

    await campaignRef.update({ 
        isActive,
        lastModifiedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    return { success: true };
});
