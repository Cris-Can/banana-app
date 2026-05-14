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
 * 🛡️ VALIDATE PASSWORD STRENGTH — Server-Side
 * Ensures password meets security requirements
 * =====================================================
 */
export const validatePasswordStrength = onCall(async (request) => {
  const { password } = request.data;

  if (!request.auth) {
    throw new HttpsError("unauthenticated", "You must be authenticated to validate a password.");
  }

  if (!password || typeof password !== "string") {
    throw new HttpsError("invalid-argument", "Password is required.");
  }

  // Password requirements
  const minLength = 8;
  const maxLength = 128;
  const hasUpperCase = /[A-Z]/.test(password);
  const hasLowerCase = /[a-z]/.test(password);
  const hasNumbers = /\d/.test(password);
  const hasSpecialChar = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password);

  const errors: string[] = [];

  if (password.length < minLength) {
    errors.push(`Password must be at least ${minLength} characters long`);
  }

  if (password.length > maxLength) {
    errors.push(`Password must not exceed ${maxLength} characters`);
  }

  if (!hasUpperCase) {
    errors.push("Password must contain at least one uppercase letter");
  }

  if (!hasLowerCase) {
    errors.push("Password must contain at least one lowercase letter");
  }

  if (!hasNumbers) {
    errors.push("Password must contain at least one number");
  }

  if (!hasSpecialChar) {
    errors.push("Password must contain at least one special character");
  }

  // Check for common patterns
  const commonPatterns = [
    /^(password|123456|12345678|qwerty|abc123)/i,
    /(.)\1{2,}/, // Same character repeated 3+ times
  ];

  for (const pattern of commonPatterns) {
    if (pattern.test(password)) {
      errors.push("Password contains a common or weak pattern");
      break;
    }
  }

  const isValid = errors.length === 0;
  const strength = isValid
    ? Math.min(5, [hasUpperCase, hasLowerCase, hasNumbers, hasSpecialChar, password.length >= 12].filter(Boolean).length)
    : 0;

  return {
    isValid,
    strength, // 0-5 scale
    errors: isValid ? [] : errors
  };
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
  const viewId = `${viewerUid}_${targetUid}_${Date.now()}`;

  try {
    await db.collection("profile_views").doc(viewId).set({
      viewerUid: viewerUid,
      targetUid: targetUid,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true, remaining: rateLimitResult.remaining };
  } catch (error: any) {
    console.error("Error recording profile view:", error);
    throw new HttpsError("internal", "Failed to record profile view");
  }
});
