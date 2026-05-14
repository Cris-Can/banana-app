import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

const db = admin.firestore();

/**
 * =====================================================
 * 🔔 A31 — RATING AGGREGATION (INCREMENTAL + TRANSACTION)
 * Trigger: When a rating is created
 * Action: Incrementally update average score for the target user
 * Uses transactions to prevent race conditions
 * Includes retry logic for reliability
 * =====================================================
 */
export const onRatingCreated = onDocumentCreated(
  "ratings/{ratingId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const ratingData = snapshot.data();
    const ratingId = event.params.ratingId;
    const fromUserId = ratingData.fromUserId;
    const toUserId = ratingData.toUserId;
    const score = ratingData.score as number;
    const ratingEventId = ratingData.eventId;

    // VALIDATION 1: Required fields
    if (!fromUserId || !toUserId || score === undefined) {
      console.log(`[RATING] Invalid rating data in ${ratingId}. Missing fields.`);
      return;
    }

    // VALIDATION 2: No self-rating
    if (fromUserId === toUserId) {
      console.log(`[RATING] Self-rating blocked for ${fromUserId} in ${ratingId}`);
      return;
    }

    // VALIDATION 3: Score must be 1-5
    if (typeof score !== "number" || score < 1 || score > 5) {
      console.log(`[RATING] Invalid score ${score} in ${ratingId}`);
      return;
    }

    // VALIDATION 4: Check for duplicate rating (same from + to + event)
    const existingRatings = await db.collection("ratings")
      .where("fromUserId", "==", fromUserId)
      .where("toUserId", "==", toUserId)
      .where("eventId", "==", ratingEventId || "")
      .limit(1)
      .get();

    if (!existingRatings.empty) {
      console.log(`[RATING] Duplicate rating blocked: ${fromUserId} → ${toUserId} for event ${ratingEventId}`);
      return;
    }

    // VALIDATION 5: Verify encounter exists (at least one confirmed encounter for this event)
    const encounters = await db.collection("encounters")
      .where("eventId", "==", ratingEventId || "")
      .where("userId1", "==", fromUserId)
      .limit(1)
      .get();

    if (encounters.empty) {
      // Try the other user order
      const encounters2 = await db.collection("encounters")
        .where("eventId", "==", ratingEventId || "")
        .where("userId2", "==", fromUserId)
        .limit(1)
        .get();

      if (encounters2.empty) {
        console.log(`[RATING] No encounter found. ${fromUserId} cannot rate ${toUserId} for event ${ratingEventId}`);
        return;
      }
    }

    console.log(`[RATING] All validations passed. ${fromUserId} → ${toUserId} score=${score}`);
    console.log(`[RATING_AGGREGATION] New rating for user ${toUserId}: ${score}. Starting incremental update...`);
    const userRef = db.collection("users").doc(toUserId);
    const maxRetries = 3;
    let lastError: Error | null = null;

    // Retry logic for transaction failures
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        await db.runTransaction(async (transaction) => {
          // 1. Get current user data
          const userDoc = await transaction.get(userRef);
          
          if (!userDoc.exists) {
            console.warn(`[RATING_AGGREGATION] User ${toUserId} not found. Creating initial rating stats.`);
            // User doesn't exist - this shouldn't happen, but handle gracefully
            transaction.set(userRef, {
              ratingSum: score,
              ratingCount: 1,
              averageScore: score,
              score: Math.round(score * 100), // For leaderboard
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });
            return;
          }

          const userData = userDoc.data() || {};
          
          // 2. Get current rating stats (with defaults for missing fields)
          const currentSum = typeof userData.ratingSum === "number" ? userData.ratingSum : 0;
          const currentCount = typeof userData.ratingCount === "number" ? userData.ratingCount : 0;
          
          // 3. Calculate new values
          const newSum = currentSum + score;
          const newCount = currentCount + 1;
          const newAverage = newCount > 0 ? newSum / newCount : 0;
          const newScoreForLeaderboard = Math.round(newAverage * 100); // For leaderboard sorting
          
          console.log(`[RATING_AGGREGATION] User ${toUserId}: Sum ${currentSum} → ${newSum}, Count ${currentCount} → ${newCount}, Avg ${newAverage.toFixed(2)}`);

          // 4. Update with transaction (atomic operation prevents race conditions)
          transaction.update(userRef, {
            ratingSum: newSum,
            ratingCount: newCount,
            averageScore: newAverage,
            score: newScoreForLeaderboard,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
          });
        });

        // Success - exit retry loop
        console.log(`[RATING_AGGREGATION] ✅ Successfully updated rating stats for ${toUserId} (attempt ${attempt})`);
        lastError = null;
        break;

      } catch (error: any) {
        lastError = error;
        console.warn(`[RATING_AGGREGATION] Transaction attempt ${attempt}/${maxRetries} failed for ${toUserId}:`, error.message);
        
        // Don't retry on certain errors
        if (error.code === "not-found" || error.code === "permission-denied") {
          console.error(`[RATING_AGGREGATION] Non-retryable error:`, error.message);
          break;
        }
        
        // Wait before retry (exponential backoff)
        if (attempt < maxRetries) {
          const delay = Math.pow(2, attempt) * 100; // 200ms, 400ms, 800ms
          await new Promise(resolve => setTimeout(resolve, delay));
        }
      }
    }

    // Log final error if all retries failed
    if (lastError) {
      console.error(`[RATING_AGGREGATION] ❌ Failed to update rating stats for ${toUserId} after ${maxRetries} attempts:`, lastError);
      // Don't throw - we don't want to fail the function completely
    }

    // 5. 🔔 PERSISTENT NOTIFICATION (Through hub)
    try {
      const numericScore = Number(score);
      if (numericScore >= 1.0) {
        const notifRef = db.collection("notifications").doc();
        await notifRef.set({
          userId: toUserId,
          title: "¡Nueva calificación! ⭐",
          message: `Has recibido una calificación de ${numericScore} estrellas.`,
          type: "RATING",
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          read: false
        });
        console.log(`[RATING_AGGREGATION] Notification created for user ${toUserId}`);
      }
    } catch (notifError) {
      // Notification failure shouldn't fail the entire function
      console.warn(`[RATING_AGGREGATION] Failed to create notification:`, notifError);
    }
  }
);
