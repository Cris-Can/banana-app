import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { checkRateLimit, cleanupRateLimits } from "./rateLimiter";

const db = admin.firestore();

/**
 * =====================================================
 * 🛡️ RATE LIMIT GUARD — Callable Function
 * Check rate limit for a specific action
 * =====================================================
 */
export const checkRateLimitGuard = onCall(async (request) => {
  // 1. Auth check (optional - some actions may be unauthenticated)
  const userId = request.auth?.uid || request.data.userId || "anonymous";
  const { action } = request.data;

  if (!action || typeof action !== "string") {
    throw new HttpsError("invalid-argument", "Action is required.");
  }

  try {
    const result = await checkRateLimit(userId, action);

    if (!result.success) {
      throw new HttpsError("resource-exhausted", result.error || "Rate limit exceeded");
    }

    return {
      success: true,
      remaining: result.remaining,
      resetAt: result.resetAt
    };
  } catch (error: any) {
    if (error instanceof HttpsError) throw error;
    console.error("Rate limit check error:", error);
    throw new HttpsError("internal", "Failed to check rate limit");
  }
});



/**
 * =====================================================
 * 🧹 SCHEDULED — CLEANUP RATE LIMITS
 * Runs daily to remove expired rate limit records
 * =====================================================
 */
export const scheduledRateLimitCleanup = onSchedule(
  { schedule: "every 24 hours", timeZone: "America/Santiago" },
  async () => {
    console.log("[RATE_LIMIT_CLEANUP] Starting scheduled cleanup...");
    await cleanupRateLimits();
  }
);

/**
 * =====================================================
 * 🛡️ RECORD PROFILE VIEW WITH RATE LIMITING
 * Callable function to record profile views with rate limit
 * =====================================================
 */
export const recordProfileView = onCall(async (request) => {
  // 1. Auth check
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const viewerUid = request.auth.uid;
  const { targetUid } = request.data;

  if (!targetUid || typeof targetUid !== "string") {
    throw new HttpsError("invalid-argument", "Target user ID is required.");
  }

  // 2. Check rate limit for profile views
  const rateLimitResult = await checkRateLimit(viewerUid, "profileView");
  if (!rateLimitResult.success) {
    throw new HttpsError("resource-exhausted", rateLimitResult.error || "Too many profile views");
  }

  // 3. Prevent viewing own profile
  if (viewerUid === targetUid) {
    return { success: true, message: "Own profile view not recorded" };
  }

  // 4. Record the profile view
  try {
    const userRef = db.collection("users").doc(targetUid);
    const viewRef = userRef.collection("profile_views").doc(viewerUid);
    
    // Get existing to check time
    const existingSnapshot = await viewRef.get();
    let lastVisit = 0;
    if (existingSnapshot.exists) {
      const timestamp = existingSnapshot.get("timestamp");
      if (timestamp) {
        lastVisit = timestamp.toDate().getTime();
      }
    }
    
    const now = Date.now();
    const shouldNotify = (now - lastVisit) > 60000;
    
    const batch = db.batch();
    
    // Set the view data
    batch.set(viewRef, {
      visitorUid: viewerUid,
      timestamp: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
    
    // If we should notify, update user doc and create notification
    if (shouldNotify) {
      batch.update(userRef, {
        profileViews: admin.firestore.FieldValue.increment(1),
        recentViewers: admin.firestore.FieldValue.arrayUnion(viewerUid)
      });
      
      const notifRef = db.collection("notifications").doc();
      batch.set(notifRef, {
        id: notifRef.id,
        userId: targetUid,
        fromUserId: viewerUid,
        title: "Tienes una nueva visita 👀",
        message: "Alguien ha visto tu perfil recientemente.",
        eventId: null,
        conversationId: null,
        read: false,
        type: "PROFILE_VIEW",
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
    }
    
    await batch.commit();

    return { success: true, remaining: rateLimitResult.remaining };
  } catch (error: any) {
    console.error("Error recording profile view:", error);
    throw new HttpsError("internal", "Failed to record profile view");
  }
});
