import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { checkRateLimit } from "./rateLimiter";

const db = admin.firestore();

/**
 * =====================================================
 * 🎯 CONFIGURACIÓN DE ESCALA Y MODO HÍBRIDO
 * =====================================================
 * NOTA DE ARQUITECTURA: 
 * Actualmente operamos en MODO HÍBRIDO. El sistema escribe en 
 * la colección 'friendships' (V2) y en los arrays 'friends' (Legacy).
 * 
 * CANDIDATO A DEPRECIACIÓN: Una vez que la App Android migre 100% 
 * a consultas sobre 'friendships', se debe apagar 
 * ENABLE_LEGACY_COMPATIBILITY para optimizar escrituras.
 */
const CONFIG = {
    MAX_FRIENDS_PER_USER: 10000,
    REQUEST_EXPIRY_DAYS: 90,
    CLEANUP_BATCH_SIZE: 500,
    TRANSACTION_TIMEOUT_MS: 5000,
    ENABLE_LEGACY_COMPATIBILITY: false, // Apagado para optimizar escrituras
};

/**
 * =====================================================
 * 🔧 HELPERS
 * =====================================================
 */



async function validateUserInteraction(senderUid: string, targetUid: string) {
    if (!targetUid || typeof targetUid !== "string") {
        return { isValid: false, error: { code: "invalid-argument", message: "El ID del destinatario es obligatorio." } };
    }
    if (senderUid === targetUid) {
        return { isValid: false, error: { code: "failed-precondition", message: "No puedes interactuar contigo mismo." } };
    }

    const [senderDoc, targetDoc] = await Promise.all([
        db.collection("users").doc(senderUid).get(),
        db.collection("users").doc(targetUid).get()
    ]);

    if (!senderDoc.exists || !targetDoc.exists) {
        return { isValid: false, error: { code: "not-found", message: "Usuario no encontrado." } };
    }

    const senderData = senderDoc.data() || {};
    const targetData = targetDoc.data() || {};

    if ((targetData.blockedUsers || []).includes(senderUid)) {
        return { isValid: false, error: { code: "permission-denied", message: "No puedes interactuar con este usuario." } };
    }
    if ((senderData.blockedUsers || []).includes(targetUid)) {
        return { isValid: false, error: { code: "permission-denied", message: "Has bloqueado a este usuario." } };
    }

    return { isValid: true, senderData, targetData };
}



/**
 * =====================================================
 * 🔧 HELPERS (TRANSACCIONALES)
 * =====================================================
 * NOTA: Todas las funciones de escritura ahora gestionan sus propios
 * contadores dentro de transacciones para garantizar la atomicidad.
 */

/**
 * Helper para obtener IDs excluidos de sugerencias (amigos y pendientes)
 */
async function getSociallyExcludedIds(currentUid: string): Promise<Set<string>> {
    const excluded = new Set<string>([currentUid]);

    try {
        const userDoc = await db.collection("users").doc(currentUid).get();
        if (!userDoc.exists) return excluded;

        const userData = userDoc.data() || {};
        
        if (Array.isArray(userData.friends)) {
            userData.friends.forEach((id: string) => excluded.add(id));
        }
        if (Array.isArray(userData.friendRequestsSent)) {
            userData.friendRequestsSent.forEach((id: string) => excluded.add(id));
        }
        if (Array.isArray(userData.friendRequestsReceived)) {
            userData.friendRequestsReceived.forEach((id: string) => excluded.add(id));
        }
    } catch (error) {
        console.error("[SOCIAL_V2] Error fetching excluded IDs:", error);
    }

    return excluded;
}

async function createNotification(data: any) {
    const notifRef = db.collection("notifications").doc();
    await notifRef.set({
        ...data,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        read: false
    });
}

/**
 * =====================================================
 * 🤝 EXPORTED FUNCTIONS V2
 * =====================================================
 */

export const sendFriendRequestV2 = onCall(async (request) => {
    const startTime = Date.now();
    const operationId = `send_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const logPrefix = `[SOCIAL_V2] ${operationId}`;

    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const senderUid = request.auth.uid;
    const { targetUid } = request.data;

    try {
        const rateLimitResult = await checkRateLimit(senderUid, "sendFriendRequest");
        if (!rateLimitResult.success) throw new HttpsError("resource-exhausted", "Demasiadas solicitudes. Intenta más tarde.");

        const validation = await validateUserInteraction(senderUid, targetUid);
        if (!validation.isValid) throw new HttpsError(validation.error!.code as any, validation.error!.message);
        const { senderData } = validation as { isValid: true; senderData: any; targetData: any };

        // Validación inicial basada en arrays (optimista)
        const senderFriends = senderData.friends || [];
        const senderSent = senderData.friendRequestsSent || [];
        const senderReceived = senderData.friendRequestsReceived || [];

        if (senderFriends.includes(targetUid)) {
            return { success: true, message: "Ya son amigos.", alreadyFriends: true };
        }

        if (senderSent.includes(targetUid)) {
            return { success: true, message: "Ya has enviado una solicitud.", alreadySent: true };
        }

        if (senderReceived.includes(targetUid)) {
            return { 
                success: false, 
                code: "CROSS_REQUEST_EXISTS", 
                message: "Este usuario ya te envió una solicitud. Por favor acéptala desde tu lista." 
            };
        }

        const serverTime = admin.firestore.FieldValue.serverTimestamp();

        await db.runTransaction(async (transaction) => {
            // Doble verificación dentro de la transacción por si hubo cambios concurrentes
            const senderSnap = await transaction.get(db.collection("users").doc(senderUid));
            const targetSnap = await transaction.get(db.collection("users").doc(targetUid));

            if (!senderSnap.exists || !targetSnap.exists) {
                throw new HttpsError("not-found", "Usuario no encontrado.");
            }

            const currentSenderData = senderSnap.data() || {};
            const currentSenderSent = currentSenderData.friendRequestsSent || [];

            if (currentSenderSent.includes(targetUid)) {
                throw new HttpsError("already-exists", "Ya existe una solicitud pendiente.");
            }

            // 3. Actualizar Contadores
            const senderUpdate: any = { 
                pendingRequestsSentCount: admin.firestore.FieldValue.increment(1)
            };
            const targetUpdate: any = { 
                pendingRequestsReceivedCount: admin.firestore.FieldValue.increment(1)
            };

            transaction.update(db.collection("users").doc(senderUid), senderUpdate);
            transaction.update(db.collection("users").doc(targetUid), targetUpdate);

            // Auditoría
            transaction.set(db.collection("social_audit").doc(`audit_${operationId}`), {
                type: "FRIEND_REQUEST_SENT",
                senderId: senderUid,
                receiverId: targetUid,
                timestamp: serverTime,
                operationId,
                legacyMode: true
            });
        });

        await createNotification({
            userId: targetUid,
            title: "Nueva solicitud de amistad 👥",
            message: `${senderData.nickname || "Alguien"} te ha enviado una solicitud.`,
            type: "FRIEND_REQUEST",
            fromUserId: senderUid
        }).catch(err => console.error("Error sending notification:", err));

        console.log(`${logPrefix} - SUCCESS: ${senderUid} -> ${targetUid} (${Date.now() - startTime}ms)`);
        return { success: true, message: "Solicitud enviada exitosamente." };
    } catch (error: any) {
        console.error(`${logPrefix} - ERROR:`, error);
        if (error instanceof HttpsError) throw error;
        throw new HttpsError("internal", "Error procesando solicitud.");
    }
});

export const acceptFriendRequestV2 = onCall(async (request) => {
    const operationId = `accept_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const startTime = Date.now();

    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const currentUid = request.auth.uid;
    const { requesterUid } = request.data;

    if (!requesterUid) throw new HttpsError("invalid-argument", "ID del solicitante requerido.");

    try {
        const rateLimitResult = await checkRateLimit(currentUid, "acceptFriendRequest");
        if (!rateLimitResult.success) throw new HttpsError("resource-exhausted", "Demasiadas operaciones.");

        const result = await db.runTransaction(async (transaction) => {
            const [currentUserSnap, requesterUserSnap] = await Promise.all([
                transaction.get(db.collection("users").doc(currentUid)),
                transaction.get(db.collection("users").doc(requesterUid))
            ]);

            if (!currentUserSnap.exists || !requesterUserSnap.exists) throw new HttpsError("not-found", "Usuario no encontrado.");

            const currentUserData = currentUserSnap.data() || {};

            const currentUserReceived = currentUserData.friendRequestsReceived || [];
            const currentUserFriends = currentUserData.friends || [];

            // 1. Verificar que la solicitud existe en el array legacy
            if (!currentUserReceived.includes(requesterUid)) {
                throw new HttpsError("not-found", "No existe solicitud pendiente entre estos usuarios.");
            }

            // 2. Idempotencia: Si ya son amigos, no hacer nada
            if (currentUserFriends.includes(requesterUid)) {
                return { alreadyFriends: true };
            }

            if ((currentUserData.friendCount || 0) >= CONFIG.MAX_FRIENDS_PER_USER) {
                throw new HttpsError("resource-exhausted", "Límite de amigos alcanzado.");
            }

            const serverTime = admin.firestore.FieldValue.serverTimestamp();

            // 3. Preparar actualizaciones de contadores
            const currentUserUpdate: any = { 
                friendCount: admin.firestore.FieldValue.increment(1),
                pendingRequestsReceivedCount: admin.firestore.FieldValue.increment(-1)
            };
            const requesterUserUpdate: any = { 
                friendCount: admin.firestore.FieldValue.increment(1),
                pendingRequestsSentCount: admin.firestore.FieldValue.increment(-1)
            };

            // Ejecutar actualizaciones
            transaction.update(db.collection("users").doc(currentUid), currentUserUpdate);
            transaction.update(db.collection("users").doc(requesterUid), requesterUserUpdate);

            // 5. Registro de Auditoría
            transaction.set(db.collection("social_audit").doc(`audit_${operationId}`), {
                type: "FRIENDSHIP_ACCEPTED",
                userIds: [currentUid, requesterUid],
                timestamp: serverTime,
                operationId,
                legacyMode: true
            });

            return { success: true, nickname: currentUserData.nickname || "Alguien" };
        });

        if (result.alreadyFriends) return { success: true, message: "Ya son amigos.", alreadyFriends: true };

        await createNotification({
            userId: requesterUid,
            title: "¡Solicitud aceptada! 👥",
            message: `${result.nickname} aceptó tu solicitud.`,
            type: "FRIEND_REQUEST_ACCEPTED",
            fromUserId: currentUid
        }).catch(err => console.error("Error sending notification:", err));

        console.log(`[SOCIAL_V2] ${operationId} SUCCESS (${Date.now() - startTime}ms)`);
        return { success: true, message: "Amistad confirmada exitosamente." };
    } catch (error: any) {
        console.error(`[SOCIAL_V2] ERROR in acceptFriendRequestV2:`, error);
        if (error instanceof HttpsError) throw error;
        throw new HttpsError("internal", "Error procesando aceptación.");
    }
});

export const rejectFriendRequestV2 = onCall(async (request) => {
    const operationId = `reject_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const startTime = Date.now();

    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const currentUid = request.auth.uid;
    const { requesterUid } = request.data;

    if (!requesterUid) throw new HttpsError("invalid-argument", "ID del solicitante requerido.");

    // Rate limiting
    const rateLimit = await checkRateLimit(currentUid, "socialAction");
    if (!rateLimit.success) {
        throw new HttpsError("resource-exhausted", rateLimit.error || "Rate limit excedido");
    }

    try {
        await db.runTransaction(async (transaction) => {
            const [currentUserSnap, requesterUserSnap] = await Promise.all([
                transaction.get(db.collection("users").doc(currentUid)),
                transaction.get(db.collection("users").doc(requesterUid))
            ]);

            if (!currentUserSnap.exists || !requesterUserSnap.exists) {
                return { alreadyProcessed: true };
            }

            const currentUserData = currentUserSnap.data() || {};

            const currentUserReceived = currentUserData.friendRequestsReceived || [];

            // Si la solicitud no existe en el array legacy, ya fue procesada
            if (!currentUserReceived.includes(requesterUid)) {
                return { alreadyProcessed: true };
            }

            // 1. Actualizar Contadores
            const currentUserUpdate: any = { pendingRequestsReceivedCount: admin.firestore.FieldValue.increment(-1) };
            const requesterUserUpdate: any = { pendingRequestsSentCount: admin.firestore.FieldValue.increment(-1) };

            if (Object.keys(currentUserUpdate).length > 0) transaction.update(db.collection("users").doc(currentUid), currentUserUpdate);
            if (Object.keys(requesterUserUpdate).length > 0) transaction.update(db.collection("users").doc(requesterUid), requesterUserUpdate);

            // Registro de Auditoría
            transaction.set(db.collection("social_audit").doc(`audit_${operationId}`), {
                type: "FRIEND_REQUEST_REJECTED",
                userIds: [currentUid, requesterUid],
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                operationId,
                legacyMode: true
            });

            return { success: true };
        });

        console.log(`[SOCIAL_V2] ${operationId} SUCCESS: ${currentUid} rejected ${requesterUid} (${Date.now() - startTime}ms)`);
        return { success: true, message: "Solicitud rechazada." };
    } catch (error: any) {
        console.error(`[SOCIAL_V2] ERROR in rejectFriendRequestV2:`, error);
        if (error instanceof HttpsError) throw error;
        throw new HttpsError("internal", "Error rechazando solicitud.");
    }
});

export const removeFriendV2 = onCall(async (request) => {
    const operationId = `remove_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const startTime = Date.now();

    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const currentUid = request.auth.uid;
    const { friendUid } = request.data;

    if (!friendUid) throw new HttpsError("invalid-argument", "ID del amigo requerido.");

    // Rate limiting
    const rateLimit = await checkRateLimit(currentUid, "socialAction");
    if (!rateLimit.success) {
        throw new HttpsError("resource-exhausted", rateLimit.error || "Rate limit excedido");
    }

    try {
        await db.runTransaction(async (transaction) => {
            const [currentUserSnap, friendUserSnap] = await Promise.all([
                transaction.get(db.collection("users").doc(currentUid)),
                transaction.get(db.collection("users").doc(friendUid))
            ]);

            if (!currentUserSnap.exists || !friendUserSnap.exists) {
                throw new HttpsError("not-found", "Usuario no encontrado.");
            }

            const currentUserData = currentUserSnap.data() || {};

            const currentUserFriends = currentUserData.friends || [];

            // 1. Validar que realmente son amigos
            if (!currentUserFriends.includes(friendUid)) {
                return { success: true, message: "No son amigos." };
            }

            // 2. Actualización y Saneamiento de Usuarios (Atómico)
            const currentUserUpdate: any = {};
            const friendUserUpdate: any = {};

            currentUserUpdate.friendCount = admin.firestore.FieldValue.increment(-1);
            friendUserUpdate.friendCount = admin.firestore.FieldValue.increment(-1);

            // Aplicar actualizaciones
            if (Object.keys(currentUserUpdate).length > 0) {
                transaction.update(db.collection("users").doc(currentUid), currentUserUpdate);
            }
            if (Object.keys(friendUserUpdate).length > 0) {
                transaction.update(db.collection("users").doc(friendUid), friendUserUpdate);
            }

            // Registro de Auditoría
            transaction.set(db.collection("social_audit").doc(`audit_${operationId}`), {
                type: "FRIENDSHIP_REMOVED",
                userIds: [currentUid, friendUid],
                wasFriend: true,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                operationId,
                legacyMode: true
            });

            return { success: true, removed: true };
        });

        console.log(`[SOCIAL_V2] ${operationId} SUCCESS: ${currentUid} removed ${friendUid} (${Date.now() - startTime}ms)`);
        return { success: true, message: "Amigo eliminado y sistema saneado." };
    } catch (error: any) {
        console.error(`[SOCIAL_V2] ERROR in removeFriendV2:`, error);
        if (error instanceof HttpsError) throw error;
        throw new HttpsError("internal", "Error eliminando amigo.");
    }
});

export const repairUserCounters = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const adminDoc = await db.collection("users").doc(request.auth.uid).get();
    if (!adminDoc.exists || !adminDoc.data()?.admin) throw new HttpsError("permission-denied", "Solo administradores.");

    const { userId } = request.data;
    if (!userId) throw new HttpsError("invalid-argument", "userId requerido.");

    // 1. Obtener documento del usuario
    const userDoc = await db.collection("users").doc(userId).get();
    if (!userDoc.exists) throw new HttpsError("not-found", "Usuario no encontrado.");
    const userData = userDoc.data() || {};

    // 2. Calcular contadores basados en la longitud de los arrays reales
    const friends = Array.isArray(userData.friends) ? userData.friends : [];
    const sent = Array.isArray(userData.friendRequestsSent) ? userData.friendRequestsSent : [];
    const received = Array.isArray(userData.friendRequestsReceived) ? userData.friendRequestsReceived : [];

    const realFriendCount = friends.length;
    const realSentCount = sent.length;
    const realReceivedCount = received.length;

    // 3. Aplicar reparación
    await db.collection("users").doc(userId).update({
        friendCount: realFriendCount,
        pendingRequestsSentCount: realSentCount,
        pendingRequestsReceivedCount: realReceivedCount
    });

    console.log(`[SOCIAL_REPAIR] Corrected counters for ${userId}`);
    return { 
        success: true, 
        friendCount: realFriendCount,
        pendingRequestsSentCount: realSentCount,
        pendingRequestsReceivedCount: realReceivedCount
    };
});



export const verifySocialSystemConsistency = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const adminDoc = await db.collection("users").doc(request.auth.uid).get();
    if (!adminDoc.exists || !adminDoc.data()?.admin) throw new HttpsError("permission-denied", "Solo administradores.");

    const { userId } = request.data;
    const userDoc = await db.collection("users").doc(userId).get();
    const userData = userDoc.data() || {};
    const arrayLength = Array.isArray(userData.friends) ? userData.friends.length : 0;
    const countValue = userData.friendCount || 0;

    return {
        success: true,
        report: {
            userId,
            friendsArrayLength: arrayLength,
            friendCountProperty: countValue,
            isConsistent: arrayLength === countValue
        }
    };
});

export const getDiscoverySuggestionsV2 = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    const currentUid = request.auth.uid;
    const { region, commune, limit = 20 } = request.data;

    try {
        const excludedIds = await getSociallyExcludedIds(currentUid);
        
        // 1. Consulta base (Prioridad: Comuna > Región > Global)
        let query: admin.firestore.Query = db.collection("users");
        
        if (commune) {
            query = query.where("commune", "==", commune);
        } else if (region) {
            query = query.where("region", "==", region);
        }

        // Pedimos el doble del límite para tener margen de filtrado social
        const snapshot = await query.limit(limit * 2).get();
        
        const ALLOWED_FIELDS = ['nickname', 'profilePictureUrl', 'age', 'commune', 'region', 'interests', 'isVerified', 'isGold', 'averageScore', 'friendCount'];

        const suggestions = snapshot.docs
            .map(doc => {
                const data = doc.data() as any;
                const filtered: any = { uid: doc.id };
                ALLOWED_FIELDS.forEach(field => {
                    if (data[field] !== undefined) {
                        filtered[field] = data[field];
                    }
                });
                return filtered;
            })
            .filter(user => !excludedIds.has(user.uid))
            .slice(0, limit);

        return { 
            success: true, 
            suggestions,
            count: suggestions.length 
        };

    } catch (error) {
        console.error("[SOCIAL_V2] Error in getDiscoverySuggestionsV2:", error);
        console.error("[SOCIAL_V2] Error in getDiscoverySuggestionsV2:", error);
        throw new HttpsError("internal", "Error obteniendo sugerencias.");
    }
});


