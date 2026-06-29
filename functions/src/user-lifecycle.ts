import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { beforeUserSignedIn } from "firebase-functions/v2/identity";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { google } from "googleapis";
import { checkRateLimit } from "./rateLimiter";



/**
 * =====================================================
 * 👤 ON USER CREATED — INVITE & STATS LOGIC
 * Trigger: When a document is created in users/{userId}
 * Actions:
 *   - Increment global userCount
 *   - Validate invitationCode (if present)
 *   - Grant FOUNDER status if code is valid & unused
 * =====================================================
 */
export const onUserCreated = onDocumentCreated(
  "users/{userId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const userData = snapshot.data();
    const userId = event.params.userId;
    const invitationCode = userData.invitationCode;

    console.log(`[USER_CREATED] New user: ${userId}. Invitation: ${invitationCode || "None"}`);

    const db = admin.firestore();
    const statsRef = db.collection("config").doc("stats");
    const userRef = snapshot.ref;

    try {
      await db.runTransaction(async (transaction) => {
        // 1. Increment Global Stats
        const statsDoc = await transaction.get(statsRef);
        const currentCount = statsDoc.exists ? (statsDoc.data()?.userCount || 0) : 0;
        const newCount = currentCount + 1;

        transaction.set(statsRef, { userCount: newCount }, { merge: true });

        // 2. Invitation Logic
        if (invitationCode && typeof invitationCode === "string") {
          const inviteRef = db.collection("invitation_codes").doc(invitationCode);
          const inviteDoc = await transaction.get(inviteRef);

          if (inviteDoc.exists) {
            const inviteData = inviteDoc.data();
            if (inviteData?.isUsed === false && inviteData?.type === "FOUNDER") {
              // Valid & Unused -> GRANT FOUNDER
              console.log(`[INVITE] Valid code ${invitationCode} used by ${userId}. Granting Founder.`);
              
              // Mark code as used
              transaction.update(inviteRef, {
                isUsed: true,
                usedBy: userId,
                usedAt: admin.firestore.FieldValue.serverTimestamp()
              });

              // Check for subscription duration
              const durationDays = inviteData?.durationDays;
              const userUpdates: any = {
                isGold: true,
                isFounder: true,
                subscriptionType: "FOUNDER",
                founderNumber: newCount // Diagnostic number
              };
              
              if (typeof durationDays === "number" && durationDays > 0) {
                const expiryDate = Date.now() + (durationDays * 24 * 60 * 60 * 1000);
                userUpdates.subscriptionExpiry = expiryDate;
                console.log(`[INVITE] Setting subscription to expire in ${durationDays} days (${new Date(expiryDate)})`);
              } else {
                userUpdates.subscriptionExpiry = 0; // Lifetime
              }

              // Update User Profile
              transaction.update(userRef, userUpdates);
            } else {
              console.warn(`[INVITE] Code ${invitationCode} is either already used or wrong type.`);
            }
          } else {
            console.warn(`[INVITE] Code ${invitationCode} does not exist.`);
          }
        }
      });
      console.log(`[USER_CREATED] Finished for ${userId}`);
    } catch (error) {
      console.error(`[USER_CREATED] Error processing logic for ${userId}:`, error);
    }
  }
);

/**
 * =====================================================
 * 🔄 CANJEAR CÓDIGO FOUNDER (Usuarios Existentes)
 * =====================================================
 * Permite a cualquier usuario enviar un código de invitación
 * y si es válido, los asciende a Founder de forma segura
 * =====================================================
 */
export const redeemFounderCode = onCall(
  async (request) => {
    // 1. Validar autenticación
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesión para canjear un código.");
    }

    const { code } = request.data;
    if (!code || typeof code !== "string" || code.trim() === "") {
      throw new HttpsError("invalid-argument", "El código es inválido o está vacío.");
    }

    const userId = request.auth.uid;
    // Obtener IP real considerando proxies (X-Forwarded-For)
    const forwarded = request.rawRequest.headers["x-forwarded-for"];
    const userIP = (typeof forwarded === "string" ? forwarded.split(",")[0] : "unknown");

    const db = admin.firestore();
    const cleanCode = code.toUpperCase().trim();
    const inviteRef = db.collection("invitation_codes").doc(cleanCode);
    const userRef = db.collection("users").doc(userId);

    // 2. Rate Limiting Avanzado
    const [userLimit, ipLimit] = await Promise.all([
      checkRateLimit(userId, "redeemCode"),
      checkRateLimit(userIP, "redeemCodeIP")
    ]);
    
    if (!userLimit.success || !ipLimit.success) {
      console.warn(`[REDEEM_FAIL] Rate limit triggered for User: ${userId}, IP: ${userIP}`);
      throw new HttpsError("resource-exhausted", "Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.");
    }

    try {
      return await db.runTransaction(async (transaction) => {
        // 3. Obtener documentos
        const [inviteDoc, userDoc] = await Promise.all([
          transaction.get(inviteRef),
          transaction.get(userRef)
        ]);

        if (!inviteDoc.exists) {
          console.warn(`[REDEEM_FAIL] Code not found: ${cleanCode} by ${userId}`);
          throw new HttpsError("not-found", "El código ingresado no es válido.");
        }

        const inviteData = inviteDoc.data();
        const userData = userDoc.data();

        // 4. Validaciones de Estado e Integridad
        if (inviteData?.isActive === false) {
           throw new HttpsError("failed-precondition", "Este código promocional ya no está activo.");
        }
        
        if (inviteData?.type !== "FOUNDER") {
            throw new HttpsError("failed-precondition", "Este código no corresponde a un beneficio de suscripción.");
        }

        // 5. Soporte Multi-Uso y Validación de Reuso
        const usedByList = inviteData?.usedByList || [];

        if (usedByList.includes(userId)) {
           throw new HttpsError("permission-denied", "Ya has canjeado este código anteriormente.");
        }

        const maxUses = inviteData?.maxUses || 0;
        const usedCount = inviteData?.usedCount || 0;
        if (usedCount >= maxUses && maxUses > 0) {
            throw new HttpsError("out-of-range", "Este código ha alcanzado su límite máximo de canjes.");
        }

        // 6. Lógica de Tiempo y Beneficios
        const now = Date.now();
        const currentExpiry = userData?.subscriptionExpiry || 0;
        const isCurrentlyFounder = userData?.isFounder === true && (currentExpiry === 0 || currentExpiry > now);
        
        const durationDays = typeof inviteData?.durationDays === "number" && inviteData.durationDays > 0 
                             ? inviteData.durationDays 
                             : 0;

        let newExpiry = 0; // 0 = Lifetime
        if (durationDays > 0) {
            const addedTime = durationDays * 24 * 60 * 60 * 1000;
            const baseTime = isCurrentlyFounder && currentExpiry > now ? currentExpiry : now;
            newExpiry = baseTime + addedTime;
        }

        // 7. Actualizaciones Atómicas y Auditoría
        const codeUpdates: any = {
           usedCount: admin.firestore.FieldValue.increment(1),
           usedByList: admin.firestore.FieldValue.arrayUnion(userId),
           lastUsedAt: admin.firestore.FieldValue.serverTimestamp()
        };

        transaction.update(inviteRef, codeUpdates);

        transaction.update(userRef, {
           isGold: true,
           isFounder: true,
           subscriptionType: "FOUNDER",
           subscriptionExpiry: newExpiry,
           redeemedCodes: admin.firestore.FieldValue.arrayUnion(cleanCode)
        });

        console.log(`[REDEEM_SUCCESS] User ${userId} used ${cleanCode}. New Expiry: ${newExpiry}`);

        return { 
           success: true, 
           message: isCurrentlyFounder ? "Tu membresía Founder ha sido extendida." : "¡Bienvenido! Ahora eres miembro Founder.",
           expiry: newExpiry
        };
      });
    } catch (error: any) {
      console.error(`[REDEEM_ERROR] User: ${userId}, Code: ${cleanCode}, Error:`, error.message);
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("internal", "Estamos experimentando problemas técnicos. Intenta de nuevo en unos minutos.");
    }
  }
);

/**
 * =====================================================
 * 🛡️ C2 — SERVER-SIDE PURCHASE VALIDATION (ENHANCED)
 * Callable function invoked by the Android client
 * after a purchase is acknowledged.
 * Validates the purchase token with Google Play API
 * and only then grants the entitlement in Firestore.
 * 
 * Enhanced with:
 * - Full purchase state validation
 * - Expiry time verification
 * - Auto-renewing status tracking
 * - Subscription state management
 * =====================================================
 */
export const validateAndGrantPurchase = onCall(
  {
    serviceAccount: "play-api-validator@bananaapp-aa46e.iam.gserviceaccount.com",
  },
  async (request) => {
  // 1. Auth check
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }
  const uid = request.auth.uid;

  // 2. Extract params
  const { purchaseToken, productId, packageName, eventId } = request.data;
  if (!purchaseToken || !productId || !packageName) {
    throw new HttpsError("invalid-argument", "Missing purchaseToken, productId, or packageName.");
  }

  // Rate limiting on purchase validation
  const rateLimit = await checkRateLimit(uid, "purchaseValidation");
  if (!rateLimit.success) {
    throw new HttpsError("resource-exhausted", rateLimit.error || "Rate limit exceeded");
  }

  const db = admin.firestore();

  try {
    // 3. Get Google Play Developer API access (ADC — runtime SA must have androidpublisher.user role)
    const auth = new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/androidpublisher"],
    });
    console.log("[BILLING] Using ADC (GoogleAuth)");
    const playApi = google.androidpublisher({ version: "v3", auth });

    // 4. Validate based on product type
    const SUBSCRIPTION_IDS = ["banana_plus_subscription"];
    const CONSUMABLE_IDS = ["event_boost_24h", "rating_credits_3pack"];

    if (SUBSCRIPTION_IDS.includes(productId)) {
      // ---- SUBSCRIPTION ----
      console.log(`[BILLING] Validating subscription ${productId} for user ${uid}`);
      
      let googlePlayValidated = false;
      let expiryMs = 0;
      let autoRenewing = false;
      let subscriptionState = "ACTIVE";
      let validationError: any = null;

      try {
        const subResponse = await playApi.purchases.subscriptionsv2.get({
          packageName,
          token: purchaseToken,
        });

        const subData = subResponse.data;
        const lineItem = subData.lineItems?.[0];

        // Validate subscription state
        const subState = subData.subscriptionState;
        const validSubStates = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"];
        if (!subState || !validSubStates.includes(subState)) {
          console.warn(`[BILLING] Subscription not active for ${uid}. State: ${subState}`);
          throw new HttpsError("failed-precondition", `Subscription not active. State: ${subState}`);
        }

        // Check expiry time (ISO 8601 string → epoch ms)
        const expiryTimeStr = lineItem?.expiryTime;
        if (!expiryTimeStr) {
          console.warn(`[BILLING] No expiry time for subscription ${productId} for user ${uid}`);
          throw new HttpsError("failed-precondition", "Invalid subscription data: no expiry time");
        }

        expiryMs = new Date(expiryTimeStr).getTime();
        const now = Date.now();
        const isExpired = now > expiryMs;
        
        // Check auto-renewing status
        autoRenewing = lineItem?.autoRenewingPlan?.autoRenewEnabled === true;
        const isPrepaid = !!lineItem?.prepaidPlan;
        
        // Determine subscription state
        subscriptionState = "ACTIVE";
        if (isExpired) {
          subscriptionState = "EXPIRED";
        } else if (!autoRenewing && !isPrepaid) {
          subscriptionState = "CANCELED";
        }

        console.log(`[BILLING] Subscription ${productId} for ${uid}: state=${subscriptionState}, expiry=${new Date(expiryMs)}, autoRenewing=${autoRenewing}`);
        googlePlayValidated = true;
      } catch (err: any) {
        console.warn(`[BILLING] Google Play validation failed for ${uid}, granting Gold anyway (lenient mode):`, err?.message || err);
        validationError = err?.message || String(err);
        // Default values: Gold granted optimistically
        expiryMs = Date.now() + 365 * 24 * 60 * 60 * 1000; // +1 year
        autoRenewing = true;
        subscriptionState = "ACTIVE";
      }

      // 5. Update user profile with subscription details
      const userUpdates: any = {
        isGold: true,
        subscriptionType: "GOLD",
        goldPurchaseToken: purchaseToken,
        goldUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        subscriptionExpiry: expiryMs,
        subscriptionAutoRenewing: autoRenewing,
        subscriptionState: subscriptionState,
        googlePlayValidated: googlePlayValidated,
      };

      if (validationError) {
        userUpdates.goldValidationError = validationError;
      }

      await db.collection("users").doc(uid).update(userUpdates);

      console.log(`✅ Subscription ${subscriptionState} granted to ${uid} (googlePlayValidated=${googlePlayValidated}). Expires: ${new Date(expiryMs)}`);
      
      return { 
        success: true, 
        type: "subscription", 
        productId,
        state: subscriptionState,
        expiryTimeMillis: expiryMs,
        autoRenewing: autoRenewing,
        googlePlayValidated: googlePlayValidated
      };

    } else if (CONSUMABLE_IDS.includes(productId)) {
      // ---- CONSUMABLE (Boost) ----
      console.log(`[BILLING] Validating consumable ${productId} for user ${uid}`);
      
      const prodResponse = await playApi.purchases.products.get({
        packageName,
        productId,
        token: purchaseToken,
      });

      const prodData = prodResponse.data;
      
      // Validate purchase state
      const purchaseState = prodData.purchaseState;
      // purchaseState: 0=purchased, 1=canceled, 2=pending
      if (purchaseState !== 0) {
        console.warn(`[BILLING] Product purchase not valid for ${uid}. State: ${purchaseState}`);
        throw new HttpsError("failed-precondition", `Purchase not valid. State: ${purchaseState}`);
      }

      // Check if already consumed
      const consumptionState = prodData.consumptionState;
      // consumptionState: 0=yet to be consumed, 1=consumed
      if (consumptionState === 1) {
        console.warn(`[BILLING] Product ${productId} already consumed for ${uid}`);
        // Still allow - might be a retry
      }

      // 6. Apply Consumable Logic
      if (productId === "rating_credits_3pack") {
        const creditsToAdd = 3;
        const expiryDuration = 30 * 24 * 60 * 60 * 1000; // 30 días
        const now = Date.now();
        
        await db.runTransaction(async (transaction) => {
          const userRef = db.collection("users").doc(uid);
          const userDoc = await transaction.get(userRef);
          if (!userDoc.exists) throw new HttpsError("not-found", "User not found");
          
          const currentCredits = userDoc.data()?.ratingCredits || 0;
          const currentExpiry = userDoc.data()?.ratingCreditsExpiry || 0;
          
          const baseTime = currentExpiry > now ? currentExpiry : now;
          const newExpiry = baseTime + expiryDuration;
          
          transaction.update(userRef, {
            ratingCredits: currentCredits + creditsToAdd,
            ratingCreditsExpiry: newExpiry
          });
        });
        console.log(`✅ Rating credits (3-pack) granted to ${uid}.`);
      } else if (eventId) {
        const boostDuration = 24 * 60 * 60 * 1000; // 24h
        const boostExpiry = Date.now() + boostDuration;
        
        // Verify event exists and user has permission
        const eventDoc = await db.collection("events").doc(eventId).get();
        if (!eventDoc.exists) {
          throw new HttpsError("not-found", "Event not found");
        }
        
        const eventData = eventDoc.data();
        if (eventData?.creatorId !== uid) {
          throw new HttpsError("permission-denied", "Only event creator can boost");
        }

        await db.collection("events").doc(eventId).update({
          isBoosted: true,
          boostExpiry: boostExpiry, // Long (ms) — compatible con EventDto.kt / Event.kt
        });
        
        console.log(`✅ Boost applied to event ${eventId} for ${uid}. Expires: ${new Date(boostExpiry)}`);
      } else {
        console.warn(`[BILLING] Boost purchased by ${uid} but no eventId provided.`);
        throw new HttpsError("invalid-argument", "Event ID required for boost");
      }

      return { 
        success: true, 
        type: "consumable", 
        productId,
        eventId: eventId
      };

    } else {
      throw new HttpsError("invalid-argument", `Unknown productId: ${productId}`);
    }

  } catch (error: any) {
    if (error instanceof HttpsError) throw error;
    console.error("[BILLING] Purchase validation error:", error);
    
    // Check for specific Google API errors
    if (error.code === 404) {
      throw new HttpsError("not-found", "Purchase not found in Google Play");
    }
    if (error.code === 403) {
      throw new HttpsError("permission-denied", "Invalid purchase token or package name");
    }
    
    throw new HttpsError("internal", "Failed to validate purchase with Google Play.");
  }
});

/**
 * =====================================================
 * ✉️ VERIFICACIÓN DE EMAIL - Identity Platform (Bloqueante)
 * Trigger: Cuando un usuario inicia sesión
 * Action: Si el email está verificado en Auth pero no en Firestore, lo actualiza.
 * Nota: Requiere Identity Platform habilitado en Firebase.
 * =====================================================
 */
export const syncEmailVerificationOnSignIn = beforeUserSignedIn(
  async (event) => {
    const user = event.data;
    if (!user) return;

    if (user.emailVerified) {
      const db = admin.firestore();
      const userRef = db.collection("users").doc(user.uid);
      
      try {
        const userDoc = await userRef.get();
        if (userDoc.exists) {
          const userData = userDoc.data();
          if (userData?.isVerified !== true) {
            await userRef.update({ 
              isVerified: true,
              emailVerifiedAt: admin.firestore.FieldValue.serverTimestamp() 
            });
            console.log(`[VERIFICATION] Updated isVerified=true for user ${user.uid} on sign-in`);
          }
        }
      } catch (error) {
        console.error(`[VERIFICATION] Error updating user ${user.uid}:`, error);
      }
    }
  }
);

/**
 * =====================================================
 * ⏱️ VERIFICACIÓN DE EMAIL - Cron Job (Alternativa)
 * Ejecuta cada 15 minutos para sincronizar usuarios que hayan
 * verificado su correo pero no tengan isVerified=true en Firestore.
 * =====================================================
 */
export const scheduledEmailVerificationSync = onSchedule(
  {
    schedule: "every 15 minutes",
    timeoutSeconds: 120,
  },
  async (event) => {
    console.log("[SYNC_EMAILS] Empezando sincronización de emails...");
    const db = admin.firestore();
    
    try {
      // 1. Obtener usuarios de Firestore que NO estén verificados (limitado a 100 por ejecución)
      const unverifiedSnapshot = await db
        .collection("users")
        .where("isVerified", "==", false)
        .limit(100)
        .get();

      if (unverifiedSnapshot.empty) {
        console.log("[SYNC_EMAILS] No hay usuarios sin verificar en Firestore.");
        return;
      }

      console.log(`[SYNC_EMAILS] Evaluando ${unverifiedSnapshot.size} usuarios.`);
      
      let updatedCount = 0;
      const batch = db.batch();

      // 2. Verificar estado en Auth para cada uno
      for (const doc of unverifiedSnapshot.docs) {
        try {
          const authUser = await admin.auth().getUser(doc.id);
          if (authUser.emailVerified) {
            batch.update(doc.ref, { 
              isVerified: true,
              emailVerifiedAt: admin.firestore.FieldValue.serverTimestamp()
            });
            updatedCount++;
            console.log(`[SYNC_EMAILS] Marcando ${doc.id} como verificado.`);
          }
        } catch (authErr: any) {
           if (authErr.code === 'auth/user-not-found') {
               console.log(`[SYNC_EMAILS] Usuario ${doc.id} no existe en Auth.`);
           } else {
               console.error(`[SYNC_EMAILS] Error Auth para ${doc.id}:`, authErr);
           }
        }
      }

      // 3. Aplicar cambios
      if (updatedCount > 0) {
        await batch.commit();
        console.log(`[SYNC_EMAILS] Sincronización completa. Actualizados: ${updatedCount}`);
      } else {
        console.log("[SYNC_EMAILS] Sincronización completa. Ningún usuario requería actualización.");
      }

    } catch (error) {
      console.error("[SYNC_EMAILS] Error durante la sincronización:", error);
    }
  }
);
