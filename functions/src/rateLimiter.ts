import * as admin from "firebase-admin";

/**
 * Rate Limiting Configuration
 */
interface RateLimitConfig {
  maxAttempts: number;
  windowMs: number; // Time window in milliseconds
}

const RATE_LIMIT_CONFIGS: Record<string, RateLimitConfig> = {
  login: { maxAttempts: 5, windowMs: 15 * 60 * 1000 }, // 5 attempts per 15 minutes
  register: { maxAttempts: 3, windowMs: 60 * 60 * 1000 }, // 3 attempts per hour
  profileView: { maxAttempts: 100, windowMs: 60 * 60 * 1000 }, // 100 views per hour
  sendMessage: { maxAttempts: 30, windowMs: 60 * 1000 }, // 30 messages per minute
  eventCreation: { maxAttempts: 5, windowMs: 60 * 60 * 1000 }, // 5 events per hour (safety net)
  rating: { maxAttempts: 10, windowMs: 5 * 60 * 1000 }, // 10 ratings per 5 minutes
  redeemCode: { maxAttempts: 5, windowMs: 15 * 60 * 1000 }, // 5 attempts per 15 mins (Account)
  redeemCodeIP: { maxAttempts: 10, windowMs: 15 * 60 * 1000 }, // 10 attempts per 15 mins (IP)
  socialAction: { maxAttempts: 10, windowMs: 60 * 1000 }, // 10 acciones sociales por minuto
  purchaseValidation: { maxAttempts: 5, windowMs: 60 * 1000 }, // 5 validaciones por minuto
};

/**
 * Check and update rate limit for a user action
 * @returns { success: boolean; remaining?: number; resetAt?: number; error?: string }
 */
export async function checkRateLimit(
  userId: string,
  action: string
): Promise<{
  success: boolean;
  remaining?: number;
  resetAt?: number;
  error?: string;
}> {
  const config = RATE_LIMIT_CONFIGS[action];
  if (!config) {
    return { success: true }; // No limit configured for this action
  }

  const db = admin.firestore();
  const docId = `${userId}_${action}`;
  const rateLimitRef = db.collection("rate_limits").doc(docId);


  try {
    return await db.runTransaction(async (transaction) => {
      const doc = await transaction.get(rateLimitRef);
      const now = Date.now();

      if (!doc.exists) {
        transaction.set(rateLimitRef, {
          count: 1,
          windowStart: now,
          lastAttempt: now,
          action: action,
          userId: userId,
        });
        return {
          success: true,
          remaining: config.maxAttempts - 1,
          resetAt: now + config.windowMs,
        };
      }

      const data = doc.data()!;
      const recordWindowStart = data.windowStart || now;

      if (now - recordWindowStart >= config.windowMs) {
        transaction.set(rateLimitRef, {
          count: 1,
          windowStart: now,
          lastAttempt: now,
          action: action,
          userId: userId,
        });
        return {
          success: true,
          remaining: config.maxAttempts - 1,
          resetAt: now + config.windowMs,
        };
      }

      const currentCount = data.count || 0;

      if (currentCount >= config.maxAttempts) {
        const resetAt = recordWindowStart + config.windowMs;
        const waitMinutes = Math.ceil((resetAt - now) / 60000);
        return {
          success: false,
          error: `Rate limit exceeded. Try again in ${waitMinutes} minute(s).`,
          resetAt: resetAt,
        };
      }

      transaction.update(rateLimitRef, {
        count: admin.firestore.FieldValue.increment(1),
        lastAttempt: now,
      });

      return {
        success: true,
        remaining: config.maxAttempts - currentCount - 1,
        resetAt: recordWindowStart + config.windowMs,
      };
    });
  } catch (error) {
    console.error(`Rate limit check failed for ${userId}/${action}:`, error);
    // Fail open - allow the request but log the error
    return { success: true };
  }
}

/**
 * Clean up old rate limit records (scheduled function)
 */
export async function cleanupRateLimits() {
  const db = admin.firestore();
  const now = Date.now();
  const maxWindowMs = Math.max(...Object.values(RATE_LIMIT_CONFIGS).map(c => c.windowMs));
  const cutoffDate = now - maxWindowMs - 60000; // Add 1 minute buffer

  try {
    const snapshot = await db
      .collection("rate_limits")
      .where("windowStart", "<", cutoffDate)
      .get();

    const batch = db.batch();
    let count = 0;

    for (const doc of snapshot.docs) {
      batch.delete(doc.ref);
      count++;

      if (count >= 450) {
        await batch.commit();
        count = 0;
      }
    }

    if (count > 0) {
      await batch.commit();
    }

    console.log(`[RATE_LIMIT_CLEANUP] Cleaned ${snapshot.size} old rate limit records`);
  } catch (error) {
    console.error("[RATE_LIMIT_CLEANUP] Error:", error);
  }
}

/**
 * Get rate limit status for debugging
 */
export async function getRateLimitStatus(
  userId: string,
  action?: string
): Promise<any> {
  const db = admin.firestore();

  const snapshot = await db
    .collection("rate_limits")
    .where("userId", "==", userId)
    .get();

  const limits: any[] = [];
  snapshot.forEach(doc => {
    const data = doc.data();
    const config = RATE_LIMIT_CONFIGS[data.action];
    if (config) {
      const now = Date.now();
      const windowStart = data.windowStart || now;
      const isNewWindow = now - windowStart >= config.windowMs;

      limits.push({
        action: data.action,
        count: data.count || 0,
        maxAttempts: config.maxAttempts,
        windowMs: config.windowMs,
        windowStart: windowStart,
        resetAt: windowStart + config.windowMs,
        isExpired: isNewWindow,
        remaining: isNewWindow ? config.maxAttempts : Math.max(0, config.maxAttempts - (data.count || 0)),
      });
    }
  });

  return { userId, limits };
}