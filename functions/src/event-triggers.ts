import * as admin from "firebase-admin";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";

const db = admin.firestore();

function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * =====================================================
 * 🆕 NOTIFICACIÓN INTELIGENTE (INTERESES + DISTANCIA)
 * - Prioridad 1: Intereses (subscribedCategories) - respects notifyByInterest
 * - Prioridad 2: Distancia (Haversine × notificationRange multiplier) - respects notifyEventsByCommune
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
    const range = eventData.notificationRange || "COMMUNE";
    const creatorId = eventData.creatorId;
    const isBoosted = eventData.isBoosted === true;

    console.log(`[INTELLIGENT_NOTIF] Event ${eventId} created. Cat: ${category}, Range: ${range}`);

    // Store both notification content AND the reason (interest/location)
    const recipientsMap = new Map<string, { title: string, message: string, reason: 'interest' | 'location' }>();

    try {
      // 🎯 STEP 1: FIND BY INTERESTS (HIGHEST PRIORITY)
      // Only query users who have notifyByInterest enabled (default true)
      const processQueryInBatches = async (
        query: admin.firestore.Query,
        processFn: (doc: admin.firestore.QueryDocumentSnapshot) => void
      ) => {
        let lastDoc: admin.firestore.DocumentSnapshot | undefined = undefined;
        let hasMore = true;
        while (hasMore) {
          let currentQuery = query.limit(500);
          if (lastDoc) {
            currentQuery = currentQuery.startAfter(lastDoc);
          }
          const snap = await currentQuery.get();
          if (snap.empty) {
            hasMore = false;
            break;
          }
          snap.forEach(processFn);
          lastDoc = snap.docs[snap.docs.length - 1];
          if (snap.docs.length < 500) {
            hasMore = false;
          }
        }
      };

      if (category) {
        const processInterestDoc = (doc: admin.firestore.QueryDocumentSnapshot) => {
          if (doc.id !== creatorId) {
            const userData = doc.data();
            const notifyByInterest = userData?.notifyByInterest !== false;
            if (notifyByInterest) {
              recipientsMap.set(doc.id, {
                title: isBoosted ? `¡Evento Destacado! 🔥 ${category}` : `Nuevo evento de ${category} para ti 🍌`,
                message: `¡Mira lo que está pasando!: ${title}`,
                reason: 'interest'
              });
            } else {
              console.log(`[INTELLIGENT_NOTIF] User ${doc.id} has notifyByInterest disabled, skipping interest notification.`);
            }
          }
        };

        const subQuery = db.collection("users").where("subscribedCategories", "array-contains", category);
        const intQuery = db.collection("users").where("interests", "array-contains", category);

        await Promise.all([
          processQueryInBatches(subQuery, processInterestDoc),
          processQueryInBatches(intQuery, processInterestDoc)
        ]);
      }

      // 🎯 STEP 2: FIND BY LOCATION (PURE DISTANCE + notificationRange multiplier)
      const RANGE_FACTOR: Record<string, number> = {
        "COMMUNE": 1.0,
        "REGION": 3.0,
        "NATIONAL": 10.0
      };
      const rangeFactor = RANGE_FACTOR[range] ?? 1.0;

      let locationQuery = db.collection("users")
        .where("notifyEventsByCommune", "==", true);
      
      if (eventData.region) {
        locationQuery = locationQuery.where("region", "==", eventData.region);
      }

      const eventLat = eventData.latitude;
      const eventLng = eventData.longitude;

      const processLocationDoc = (doc: admin.firestore.QueryDocumentSnapshot) => {
        if (doc.id === creatorId || recipientsMap.has(doc.id)) return;
        const userData = doc.data();
        const userLat = userData.latitude;
        const userLng = userData.longitude;
        const userRadius = userData.searchRadiusKm ?? 20;

        if (userLat != null && userLng != null && eventLat != null && eventLng != null) {
          const distance = haversineKm(eventLat, eventLng, userLat, userLng);
          if (distance > userRadius * rangeFactor) return;
        }

        recipientsMap.set(doc.id, {
          title: isBoosted ? "¡Evento Destacado cerca de ti! 🔥" : "Nuevo evento cerca de ti 📍",
          message: `Se creó un evento en tu zona: ${title}`,
          reason: 'location'
        });
      };

      await processQueryInBatches(locationQuery, processLocationDoc);

      // 🎯 STEP 3: PERSIST IN BATCH
      if (recipientsMap.size === 0) {
        console.log("[INTELLIGENT_NOTIF] No recipients found (all filtered by preferences).");
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

      const fetchPromises = [];
      for (let i = 0; i < recipientArray.length; i += chunkSize) {
        const chunk = recipientArray.slice(i, i + chunkSize);
        fetchPromises.push(
          db.collection("users")
            .where(admin.firestore.FieldPath.documentId(), "in", chunk)
            .get()
        );
      }

      const snapshots = await Promise.all(fetchPromises);
      const allUserDocs = snapshots.flatMap(snap => snap.docs);

      allUserDocs.forEach(userDoc => {
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

    if (JSON.stringify(oldApproved) === JSON.stringify(newApproved)) {
      console.log("No participant change, skipping");
      return;
    }

    // Detect newly approved users (in new list but not in old)
    const newlyApproved = newApproved.filter(
      (uid) => !oldApproved.includes(uid)
    );

    if (newlyApproved.length === 0) return;

    const batch = db.batch();
    for (const uid of newlyApproved) {
      const userRef = db.collection("users").doc(uid);
      batch.update(userRef, {
        eventsRequestedCount: admin.firestore.FieldValue.increment(1),
        joinRequestsInCycle: admin.firestore.FieldValue.increment(1),
      });
    }

    try {
      await batch.commit();
      console.log(`Stats incremented for ${newlyApproved.length} approved users`);
    } catch (error) {
      console.error(`Failed to batch increment stats:`, error);
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

    try {
      await db.runTransaction(async (transaction) => {
        const userRef = db.collection("users").doc(creatorId);
        const userDoc = await transaction.get(userRef);

        if (!userDoc.exists) {
          console.error("Creator not found, deleting event:", eventId);
          await snapshot.ref.delete();
          return;
        }

        const userData = userDoc.data()!;
        const subscriptionType = userData.subscriptionType || "FREE";
        const isFounder = userData.isFounder === true;

        // Gold/Founder: unlimited
        if (subscriptionType === "GOLD" || subscriptionType === "FOUNDER" || isFounder) {
          transaction.update(userRef, {
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
          transaction.update(userRef, {
            currentCycleStartDate: now,
            eventsCreatedInCycle: 1,
            joinRequestsInCycle: 0,
            adEventsUnlocked: 0,
            adsWatchedProgress: 0,
          });
          console.log(`Cycle reset + event created for ${creatorId}`);
          return;
        }

        const effectiveLimit = FREE_LIMIT + adsUnlocked;

        if (eventsCreated >= effectiveLimit) {
          // LIMIT EXCEEDED — Mark event for deletion
          // NOTE: Can't delete inside transaction from different collection easily,
          // so we set a flag and delete after commit
          transaction.update(userRef, {
            _shouldDeleteEvent: eventId // temp marker
          });
          return;
        }

        // Within limits: increment
        transaction.update(userRef, {
          eventsCreatedInCycle: admin.firestore.FieldValue.increment(1),
        });
        console.log(`Event ${eventId} created. Count: ${eventsCreated + 1}/${effectiveLimit}`);
      });

      // Post-transaction: check if event should be deleted
      try {
        const userDocAfter = await db.collection("users").doc(creatorId).get();
        if (userDocAfter.data()?._shouldDeleteEvent === eventId) {
          await db.collection("users").doc(creatorId).update({
            _shouldDeleteEvent: admin.firestore.FieldValue.delete()
          });
          await snapshot.ref.delete();

          await db.collection("notifications").doc().set({
            userId: creatorId,
            title: "Límite alcanzado",
            message: "Has alcanzado tu límite mensual de eventos. Suscríbete a +panoramas Gold para crear más.",
            eventId: "",
            type: "GENERIC",
            read: false,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
      } catch (deleteError) {
        console.error("Failed to handle event deletion post-transaction:", deleteError);
        // Best effort cleanup: remove marker even if delete failed
        try {
          await db.collection("users").doc(creatorId).update({
            _shouldDeleteEvent: admin.firestore.FieldValue.delete()
          });
        } catch (cleanupError) {
          console.error("Failed to cleanup _shouldDeleteEvent marker:", cleanupError);
        }
      }

    } catch (error) {
      console.error("Error validating event creation:", error);
    }
  }
);
