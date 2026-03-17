import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { google } from "googleapis";

import * as admin from "firebase-admin";

admin.initializeApp();

/**
 * 🧹 Helper to cleanup invalid FCM tokens
 */
async function cleanupInvalidToken(userId: string, error: any) {
  const adminCodes = [
    "messaging/registration-token-not-registered",
    "messaging/invalid-registration-token",
  ];

  if (error.code && adminCodes.includes(error.code)) {
    console.log(`Cleaning up invalid token for user ${userId} due to ${error.code}`);
    try {
      await admin.firestore().collection("users").doc(userId).update({
        fcmToken: admin.firestore.FieldValue.delete(),
      });
    } catch (e) {
      console.error(`Failed to delete token for ${userId}`, e);
    }
  }
}

/**
 * =====================================================
 * 🔔 EXISTENTE — ENVÍO DE PUSH AL CREAR NOTIFICACIÓN
 * =====================================================
 */
export const onNotificationCreated = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const notification = snapshot.data();
    const userId = notification.userId;

    const db = admin.firestore();
    const userSnap = await db.collection("users").doc(userId).get();

    if (!userSnap.exists) {
      console.log(`[PUSH_DIAG] Usuario no existe: ${userId}`);
      return;
    }

    const userData = userSnap.data();
    const fcmToken = userData?.fcmToken;

    if (!fcmToken) {
      console.log(`[PUSH_DIAG] Usuario ${userId} (${userData?.nickname || "sin nick"}) NO tiene token FCM.`);
      return;
    }

    const notificationType = notification.type || "GENERIC";
    let channelId = "banana_channel_01";

    // 📺 CHANNEL MAPPING (Matching NotificationHelper.kt)
    switch (notificationType) {
      case "NEW_MESSAGE":
        channelId = "banana_messages";
        break;
      case "JOIN_REQUEST_SENT":
      case "JOIN_APPROVED":
      case "JOIN_REJECTED":
      case "RATING":
      case "RATING_REMINDER":
        channelId = "banana_reminders";
        break;
      case "EVENT_CREATED":
      case "EVENT_WALL_POST":
      case "EVENT_UPDATE":
        channelId = "banana_channel_01";
        break;
    }

    const message = {
      token: fcmToken,
      notification: {
        title: notification.title,
        body: notification.message,
      },
      data: {
        eventId: notification.eventId ?? "",
        conversationId: notification.conversationId ?? notification.eventId ?? "",
        fromUserId: notification.fromUserId ?? "",
        type: notificationType,
        channelId: channelId,
      },
      android: {
        priority: "high" as const,
        notification: {
          channelId: channelId,
          sound: "default",
          // Sin clickAction → Android abre la app automáticamente al tocar la notificación
        }
      },
      apns: {
        payload: {
          aps: {
            contentAvailable: true,
            sound: "default"
          }
        }
      }
    };

    try {
      await admin.messaging().send(message);
      console.log(`[PUSH_DIAG] Enviado ok a ${userId}. Tipo: ${notificationType}. Canal: ${channelId}`);
    } catch (e: any) {
      console.error(`[PUSH_DIAG] Error en ${userId}:`, e);
      await cleanupInvalidToken(userId, e);
    }
  }
);

/**
 * =====================================================
 * 🆕 NOTIFICACIÓN INTELIGENTE (ZONA + INTERESES)
 * - Prioridad 1: Intereses (subscribedCategories)
 * - Prioridad 2: Ubicación (notificationRange)
 * - Evita duplicados por usuario
 * =====================================================
 */
export const onEventCreatedNotifyZone = onDocumentCreated(
  "events/{eventId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const eventData = snapshot.data();
    const eventId = event.params.eventId;
    const title = eventData.title;
    const category = eventData.category;
    const commune = eventData.commune;
    const region = eventData.region;
    const range = eventData.notificationRange || "COMMUNE";
    const creatorId = eventData.creatorId;
    const isBoosted = eventData.isBoosted === true;

    console.log(`[INTELLIGENT_NOTIF] Event ${eventId} created. Cat: ${category}, Range: ${range}`);

    const db = admin.firestore();
    const recipientsMap = new Map<string, { title: string, message: string }>();

    try {
      // 🎯 STEP 1: FIND BY INTERESTS (HIGHEST PRIORITY)
      if (category) {
        // Query 1: Subscribed Categories
        const subSnap = await db.collection("users")
          .where("subscribedCategories", "array-contains", category)
          .get();

        // Query 2: Profile Interests (Gustos)
        const intSnap = await db.collection("users")
          .where("interests", "array-contains", category)
          .get();

        const processSnap = (snap: admin.firestore.QuerySnapshot) => {
          snap.forEach(doc => {
            if (doc.id !== creatorId) {
              recipientsMap.set(doc.id, {
                title: isBoosted ? `¡Evento Destacado! 🔥 ${category}` : `Nuevo evento de ${category} para ti 🍌`,
                message: `¡Mira lo que está pasando!: ${title}`
              });
            }
          });
        };

        processSnap(subSnap);
        processSnap(intSnap);
      }

      // 🎯 STEP 2: FIND BY LOCATION (ONLY IF NOT NOTIFIED YET)
      let locationQuery = db.collection("users").where("notifyEventsByCommune", "==", true);

      if (range === "COMMUNE" && commune) {
        locationQuery = locationQuery.where("commune", "==", commune);
      } else if (range === "REGION" && region) {
        locationQuery = locationQuery.where("region", "==", region);
      } else if (range === "NATIONAL") {
        // No extra filter (Chile by default)
      } else {
        if (commune) locationQuery = locationQuery.where("commune", "==", commune);
      }

      const locationUsers = await locationQuery.get();

      locationUsers.forEach(doc => {
        // ONLY ADD if not already added by interests
        if (doc.id !== creatorId && !recipientsMap.has(doc.id)) {
          recipientsMap.set(doc.id, {
            title: isBoosted ? "¡Evento Destacado cerca de ti! 🔥" : "Nuevo evento cerca de ti 📍",
            message: `Se creó un evento en tu zona: ${title}`
          });
        }
      });

      // 🎯 STEP 3: PERSIST IN BATCH
      if (recipientsMap.size === 0) {
        console.log("[INTELLIGENT_NOTIF] No recipients found.");
        return;
      }

      console.log(`[INTELLIGENT_NOTIF] Creating ${recipientsMap.size} unique notifications.`);
      const batch = db.batch();
      let batchSize = 0;

      for (const [userId, content] of recipientsMap.entries()) {
        const notifRef = db.collection("notifications").doc();
        batch.set(notifRef, {
          userId: userId,
          title: content.title,
          message: content.message,
          eventId: eventId,
          type: "EVENT_CREATED",
          read: false,
          createdAt: admin.firestore.FieldValue.serverTimestamp()
        });
        batchSize++;

        if (batchSize >= 450) { // Firestore limit
          await batch.commit();
          console.log("[INTELLIGENT_NOTIF] Intermediate batch committed.");
        }
      }

      if (batchSize > 0) {
        await batch.commit();
        console.log("[INTELLIGENT_NOTIF] Final batch committed.");
      }

    } catch (error) {
      console.error("[INTELLIGENT_NOTIF] Major error:", error);
    }
  }
);
/**
 * =====================================================
 * 🔔 A15.1 — NOTIFICACIONES DE MODERACIÓN
 * =====================================================
 */
export const onEventUpdatedNotifyModeration = onDocumentCreated(
  "events/{eventId}",
  async () => {
    // 👉 Ya tienes el sistema de notifications listo
    // 👉 Las notificaciones se crean desde EventRepository
    // 👉 Esta función NO es obligatoria en esta fase
    // 👉 La dejamos documentada para A15.2+
  }
);


/**
 * =====================================================
 * 🔔 A31 — RATING AGGREGATION (AUTO-UPDATE)
 * Trigger: When a rating is created
 * Action: Recalculate average score for the target user
 * =====================================================
 */
export const onRatingCreated = onDocumentCreated(
  "ratings/{ratingId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const ratingData = snapshot.data();
    const toUserId = ratingData.toUserId;
    const score = ratingData.score;

    if (!toUserId || score === undefined) {
      console.log("Invalid rating data:", event.params.ratingId);
      return;
    }

    console.log(`New rating for user ${toUserId}. Recalculating...`);

    const db = admin.firestore();

    try {
      // 1. Fetch ALL ratings for this user (ensure accuracy)
      // (Alternative: Increment logic, but full recalc is safer)
      const ratingsSnapshot = await db
        .collection("ratings")
        .where("toUserId", "==", toUserId)
        .get();

      let sum = 0;
      let count = 0;

      ratingsSnapshot.forEach((doc) => {
        const s = doc.data().score;
        if (typeof s === "number") {
          sum += s;
          count++;
        }
      });

      console.log(`Stats for ${toUserId}: Sum=${sum}, Count=${count}`);

      // 2. Update User Profile
      const avgScore = count > 0 ? Math.round((sum / count) * 100) : 0; // Score for leaderboard sorting
      await db.collection("users").doc(toUserId).update({
        ratingSum: sum,
        ratingCount: count,
        score: avgScore, // 🏆 Used by LeaderboardScreen for ranking
      });

      console.log(`Profile updated for ${toUserId}`);

      // 3. 🔔 PERSISTENT NOTIFICATION (Through hub)
      const numericScore = Number(score);
      console.log(`Evaluating notification for ${toUserId}: NumericScore=${numericScore}`);

      if (numericScore >= 1.0) { // Changed to all ratings for visibility, test if 4.0 is better
        const notifRef = db.collection("notifications").doc();
        await notifRef.set({
          userId: toUserId,
          title: "¡Nueva calificación! ⭐",
          message: `Has recibido una calificación de ${numericScore} estrellas.`,
          type: "RATING",
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          read: false
        });
        console.log(`Notification document created for user ${toUserId}`);
      }
    } catch (error) {
      console.error("Error updating user rating stats:", error);
    }
  }
);


/**
 * =====================================================
 * 🔔 A45 — RATING REMINDER (Round 9)
 * Trigger: When an event's 'canBeRated' is set to true
 * Action: Notify all participants to rate
 * =====================================================
 */
export const onEventRatableReminder = onDocumentUpdated(
  "events/{eventId}",
  async (event) => {
    const change = event.data;
    if (!change) return;

    const before = change.before.data();
    const after = change.after.data();

    // Trigger ONLY when canBeRated changes from false to true
    if (after.canBeRated === true && before.canBeRated !== true) {
      console.log(`Event ${event.params.eventId} is now ratable. Sending reminders...`);
      const db = admin.firestore();

      const participantIds: string[] = after.approvedParticipants || [];
      const creatorId: string = after.creatorId;
      const allConcerned = Array.from(new Set([...participantIds, creatorId]));

      const batch = db.batch();
      for (const userId of allConcerned) {
        const notifRef = db.collection("notifications").doc();
        batch.set(notifRef, {
          userId: userId,
          title: "¡Evento finalizado! ⭐",
          message: `Ya puedes calificar a los asistentes de "${after.title || "el evento"}"`,
          eventId: event.params.eventId,
          type: "RATING_REMINDER",
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          read: false
        });
      }
      await batch.commit();
      console.log(`Created ${allConcerned.length} rating reminder notifications.`);
    }
  }
);


/**
 * =====================================================
 * 🔔 A39 — WALL POST NOTIFICATIONS (SERVER-SIDE)
 * Trigger: When a post is created in events/{eventId}/feed/{postId}
 * Action: Notify all participants (except author)
 * =====================================================
 */
export const onFeedPostCreated = onDocumentCreated(
  "events/{eventId}/feed/{postId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const post = snapshot.data();
    const eventId = event.params.eventId;

    // Data from Post
    const authorId = post.userId;
    const authorNickname = post.userNickname || "Alguien";
    const content = post.content || "Nueva foto";

    console.log(`New post by ${authorId} in event ${eventId}`);

    const db = admin.firestore();

    try {
      // 1. Get Event Data (Title & Participants)
      const eventDoc = await db.collection("events").doc(eventId).get();
      if (!eventDoc.exists) {
        console.log("Event not found");
        return;
      }

      const eventData = eventDoc.data();
      const eventTitle = eventData?.title || "Evento";
      const creatorId = eventData?.creatorId;
      const approvedParticipants = eventData?.approvedParticipants || [];

      // Target Audience: Creator + Approved Participants
      const recipients = new Set<string>([...approvedParticipants]);
      if (creatorId) recipients.add(creatorId);

      // Remove Author
      recipients.delete(authorId);

      // 🛑 REMOVE REPLIED USER (Robust Exclusion using replyToUserId)
      const replyToUserId = post.replyToUserId;
      if (replyToUserId && typeof replyToUserId === "string") {
        const trimmedRepliedId = replyToUserId.trim();
        console.log(`[EXCLUSION_DIAG] Checking exclusion for userId: [${trimmedRepliedId}]`);
        if (recipients.has(trimmedRepliedId)) {
          recipients.delete(trimmedRepliedId);
          console.log(`[EXCLUSION_DIAG] Successfully excluded user ${trimmedRepliedId} from generic notification.`);
        } else {
          console.log(`[EXCLUSION_DIAG] User ${trimmedRepliedId} was not in recipients (Author id: ${authorId})`);
        }
      } else {
        console.log("[EXCLUSION_DIAG] No valid replyToUserId found in post.");
      }

      console.log(`Final recipients list size: ${recipients.size}. Recipients: ${Array.from(recipients).join(", ")}`);

      if (recipients.size === 0) {
        console.log("No recipients for this post.");
        return;
      }

      console.log(`Targeting ${recipients.size} users for notification.`);

      // 2. Batch Create Notifications
      // We must fetch users to check "notifyEventWall" preference AND fcmToken
      // Optimization: Split into chunks of 10 or fetch individually...
      // Firestore 'in' query supports up to 10? 30?
      // Since maxParticipants is low (e.g. 50), we can iterate.

      // Let's iterate and create notifications.
      // Ideally we should use multicast FCM, but we stick to the project pattern:
      // Create 'notifications' document -> Trigger 'onNotificationCreated' -> FCM
      // OR direct FCM if we want speed and less DB writes? 
      // The project uses "onNotificationCreated" trigger as the central hub (see top of file).
      // So we just create docs in "notifications" collection.

      const batch = db.batch();
      let count = 0;

      // Fetch user preferences in parallel?
      // For scalability, we shouldn't fetch 50 documents one by one.
      // But for <50 participants, it's acceptable for now.

      // Let's check 'notifyEventWall' (default true in model, but we need to check DB).
      // The onEventCreatedNotifyCommune fetch users by query. Here we have IDs.
      // We'll trust the Client to have updated 'onNotificationCreated' logic,
      // but actually 'onNotificationCreated' sends the push.
      // So we just insert documents.

      // WAIT! If we blindly insert, we might spam users who disabled it.
      // We SHOULD check the preference.

      // Option A: Fetch all user docs (costly).
      // Option B: Query users where uid IN [...] AND notifyEventWall == true (limit 30).

      const recipientArray = Array.from(recipients);
      const chunkSize = 10; // 'in' query limit is 30, keep safe.

      for (let i = 0; i < recipientArray.length; i += chunkSize) {
        const chunk = recipientArray.slice(i, i + chunkSize);

        const usersSnap = await db.collection("users")
          .where(admin.firestore.FieldPath.documentId(), "in", chunk)
          .get();

        usersSnap.forEach(userDoc => {
          const userData = userDoc.data();
          // Check Preference
          if (userData.notifyEventWall !== false) { // Default is true if undefined
            const notifRef = db.collection("notifications").doc();

            const shortContent = content.length > 30 ? content.substring(0, 30) + "..." : content;

            batch.set(notifRef, {
              userId: userDoc.id,
              title: `Nuevo mensaje en ${eventTitle}`,
              message: `${authorNickname}: ${shortContent}`,
              eventId: eventId,
              conversationId: eventId, // Use eventId as conversationId for wall posts
              fromUserId: authorId,
              type: "EVENT_WALL_POST",
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
              read: false
            });
            count++;
          }
        });
      }

      if (count > 0) {
        await batch.commit();
        console.log(`Created ${count} notifications for wall post.`);
      } else {
        console.log("No notifications created (prefs disabled or error).");
      }

    } catch (error) {
      console.error("Error sending wall notifications:", error);
    }
  }
);


/**
 * =====================================================
 * 📊 STATS — INCREMENT COUNTERS ON PARTICIPANT APPROVAL
 * Trigger: When event.approvedParticipants changes
 * Actions:
 *   - Increment eventsRequestedCount (reliability stat)
 *   - Increment joinRequestsInCycle (subscription limit tracking)
 * =====================================================
 */
export const onParticipantApproved = onDocumentUpdated(
  "events/{eventId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    const oldApproved: string[] = before.approvedParticipants || [];
    const newApproved: string[] = after.approvedParticipants || [];

    // Detect newly approved users (in new list but not in old)
    const newlyApproved = newApproved.filter(
      (uid) => !oldApproved.includes(uid)
    );

    if (newlyApproved.length === 0) return;

    const db = admin.firestore();

    for (const uid of newlyApproved) {
      try {
        await db.collection("users").doc(uid).update({
          eventsRequestedCount: admin.firestore.FieldValue.increment(1),
          joinRequestsInCycle: admin.firestore.FieldValue.increment(1),
        });
        console.log(`Stats incremented for approved user ${uid}`);
      } catch (error) {
        console.error(`Failed to increment stats for ${uid}:`, error);
      }
    }
  }
);


/**
 * =====================================================
 * 🛡️ SERVER-SIDE VALIDATION — EVENT CREATION LIMITS
 * Trigger: When a new event is created
 * Actions:
 *   - Check if creator exceeded free tier limits
 *   - Reset cycle if 30+ days passed
 *   - Increment eventsCreatedInCycle counter
 *   - Delete event if limit exceeded (enforcement)
 * =====================================================
 */
export const onEventCreatedValidation = onDocumentCreated(
  "events/{eventId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const eventData = snapshot.data();
    const eventId = event.params.eventId;
    const creatorId = eventData.creatorId;

    if (!creatorId) {
      console.error("Event without creatorId, deleting:", eventId);
      await snapshot.ref.delete();
      return;
    }

    const db = admin.firestore();

    try {
      const userDoc = await db.collection("users").doc(creatorId).get();
      if (!userDoc.exists) {
        console.error("Creator not found, deleting event:", eventId);
        await snapshot.ref.delete();
        return;
      }

      const userData = userDoc.data()!;
      const subscriptionType = userData.subscriptionType || "FREE";
      const isFounder = userData.isFounder === true;

      // Gold/Founder: unlimited creation
      if (subscriptionType === "GOLD" || subscriptionType === "FOUNDER" || isFounder) {
        // Just increment the counter, no limit enforced
        await db.collection("users").doc(creatorId).update({
          eventsCreatedInCycle: admin.firestore.FieldValue.increment(1),
        });
        console.log(`Event ${eventId} created by premium user ${creatorId}`);
        return;
      }

      // Free tier: check limits
      const FREE_LIMIT = 1;
      const cycleStart = userData.currentCycleStartDate || 0;
      const now = Date.now();
      const daysDiff = Math.floor((now - cycleStart) / (1000 * 60 * 60 * 24));

      let eventsCreated = userData.eventsCreatedInCycle || 0;
      const adsUnlocked = userData.adEventsUnlocked || 0;

      // Lazy cycle reset (30 days)
      if (daysDiff >= 30) {
        await db.collection("users").doc(creatorId).update({
          currentCycleStartDate: now,
          eventsCreatedInCycle: 1, // This event counts as the first
          joinRequestsInCycle: 0,
          adEventsUnlocked: 0,
          adsWatchedProgress: 0,
        });
        console.log(`Cycle reset + event created for ${creatorId}`);
        return;
      }

      const effectiveLimit = FREE_LIMIT + adsUnlocked;

      if (eventsCreated >= effectiveLimit) {
        // LIMIT EXCEEDED — Delete event and notify
        console.warn(`Creator ${creatorId} exceeded limit (${eventsCreated}/${effectiveLimit}). Deleting event ${eventId}`);
        await snapshot.ref.delete();

        // Notify user
        await db.collection("notifications").doc().set({
          userId: creatorId,
          title: "Límite alcanzado",
          message: "Has alcanzado tu límite mensual de eventos. Suscríbete a Banana Gold para crear más.",
          eventId: "",
          type: "GENERIC",
          read: false,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return;
      }

      // Within limits: increment counter
      await db.collection("users").doc(creatorId).update({
        eventsCreatedInCycle: admin.firestore.FieldValue.increment(1),
      });
      console.log(`Event ${eventId} created. Count: ${eventsCreated + 1}/${effectiveLimit}`);

    } catch (error) {
      console.error("Error validating event creation:", error);
    }
  }
);


/**
 * =====================================================
 * 🛡️ C2 — SERVER-SIDE PURCHASE VALIDATION
 * Callable function invoked by the Android client
 * after a purchase is acknowledged.
 * Validates the purchase token with Google Play API
 * and only then grants the entitlement in Firestore.
 * =====================================================
 */
export const validateAndGrantPurchase = onCall(async (request) => {
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

  const db = admin.firestore();

  try {
    // 3. Get Google Play Developer API access via default service account
    const auth = new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/androidpublisher"],
    });
    const playApi = google.androidpublisher({ version: "v3", auth });

    // 4. Validate based on product type
    const SUBSCRIPTION_IDS = ["banana_plus_monthly"];
    const CONSUMABLE_IDS = ["event_boost_24h"];

    if (SUBSCRIPTION_IDS.includes(productId)) {
      // ---- SUBSCRIPTION ----
      const subResponse = await playApi.purchases.subscriptions.get({
        packageName,
        subscriptionId: productId,
        token: purchaseToken,
      });

      const paymentState = subResponse.data.paymentState;
      // paymentState: 0=pending, 1=received, 2=free trial, 3=deferred
      if (paymentState === undefined || (paymentState !== 1 && paymentState !== 2)) {
        console.warn(`Subscription payment not confirmed for ${uid}. State: ${paymentState}`);
        throw new HttpsError("failed-precondition", "Subscription payment not confirmed.");
      }

      // 5. Grant Gold status
      await db.collection("users").doc(uid).update({
        isGold: true,
        subscriptionType: "GOLD",
        goldPurchaseToken: purchaseToken,
        goldUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      console.log(`✅ Gold granted to ${uid} via server validation.`);
      return { success: true, type: "subscription", productId };

    } else if (CONSUMABLE_IDS.includes(productId)) {
      // ---- CONSUMABLE (Boost) ----
      const prodResponse = await playApi.purchases.products.get({
        packageName,
        productId,
        token: purchaseToken,
      });

      const purchaseState = prodResponse.data.purchaseState;
      // purchaseState: 0=purchased, 1=canceled
      if (purchaseState !== 0) {
        console.warn(`Product purchase not valid for ${uid}. State: ${purchaseState}`);
        throw new HttpsError("failed-precondition", "Purchase not valid.");
      }

      // 6. Apply Boost if eventId provided
      if (eventId) {
        const boostDuration = 24 * 60 * 60 * 1000; // 24h
        const boostUntil = Date.now() + boostDuration;
        await db.collection("events").doc(eventId).update({
          isBoosted: true,
          boostUntil: new Date(boostUntil),
        });
        console.log(`✅ Boost applied to event ${eventId} for ${uid}.`);
      } else {
        console.warn(`Boost purchased by ${uid} but no eventId provided.`);
      }

      return { success: true, type: "consumable", productId };

    } else {
      throw new HttpsError("invalid-argument", `Unknown productId: ${productId}`);
    }

  } catch (error: any) {
    if (error instanceof HttpsError) throw error;
    console.error("Purchase validation error:", error);
    throw new HttpsError("internal", "Failed to validate purchase with Google Play.");
  }
});
