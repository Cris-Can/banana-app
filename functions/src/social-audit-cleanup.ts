import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";

const CLEANUP_BATCH_SIZE = 500;

/**
 * Cleanup expired social_audit documents daily.
 * Documents older than 90 days (based on expiresAt field) are deleted in batches.
 */
export const cleanupExpiredSocialAudit = onSchedule(
  { schedule: "every 24 hours", timeZone: "America/Santiago" },
  async () => {
    const db = admin.firestore();
    const now = admin.firestore.Timestamp.now();
    let totalDeleted = 0;

    console.log("[SOCIAL_AUDIT_CLEANUP] Starting cleanup of expired audit logs...");

    while (true) {
      const expired = await db.collection("social_audit")
        .where("expiresAt", "<", now)
        .limit(CLEANUP_BATCH_SIZE)
        .get();

      if (expired.empty) break;

      const batch = db.batch();
      expired.docs.forEach(doc => batch.delete(doc.ref));
      await batch.commit();

      totalDeleted += expired.size;
      console.log(`[SOCIAL_AUDIT_CLEANUP] Deleted ${expired.size} documents (total: ${totalDeleted})`);

      if (expired.size < CLEANUP_BATCH_SIZE) break;
    }

    console.log(`[SOCIAL_AUDIT_CLEANUP] Cleanup complete. Total deleted: ${totalDeleted}`);
  }
);
