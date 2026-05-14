import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";



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
      case "EVENT_CANCELLED":
      case "EVENT_CLOSED":
      case "REMOVED_FROM_EVENT":
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
 * 🧹 SCHEDULED: CLEANUP ORPHANED FCM TOKENS
 * =====================================================
 */
export const cleanupOrphanedTokens = onSchedule(
  { schedule: "every 24 hours", timeZone: "America/Santiago" },
  async () => {
    const db = admin.firestore();
    console.log("[TOKEN_CLEANUP] Starting orphaned token cleanup...");

    const INVALID_CODES = [
      "messaging/registration-token-not-registered",
      "messaging/invalid-registration-token",
    ];
    const BATCH_SIZE = 500;

    try {
      const usersWithTokens = await db.collection("users")
        .where("fcmToken", "!=", "")
        .get();

      console.log(`[TOKEN_CLEANUP] Found ${usersWithTokens.size} users with tokens.`);

      // Mapear docs por token para poder recuperar el doc al procesar resultados
      const tokenToDocId = new Map<string, string>();
      const allTokens: string[] = [];

      for (const userDoc of usersWithTokens.docs) {
        const token = userDoc.data().fcmToken;
        if (token && typeof token === "string") {
          allTokens.push(token);
          tokenToDocId.set(token, userDoc.id);
        }
      }

      let cleaned = 0;
      let valid = 0;

      // Procesar en lotes de BATCH_SIZE
      for (let i = 0; i < allTokens.length; i += BATCH_SIZE) {
        const batchTokens = allTokens.slice(i, i + BATCH_SIZE);

        const response = await admin.messaging().sendEachForMulticast(
          {
            tokens: batchTokens,
            data: { type: "TOKEN_VALIDATION" },
            android: { priority: "normal" as const },
          },
          true // dryRun = true → no envía realmente
        );

        const firestoreBatch = db.batch();
        let batchHasDeletes = false;

        response.responses.forEach((result, index) => {
          const token = batchTokens[index];
          if (result.success) {
            valid++;
          } else {
            const errorCode = result.error?.code ?? "";
            if (INVALID_CODES.includes(errorCode)) {
              const docId = tokenToDocId.get(token);
              if (docId) {
                firestoreBatch.update(db.collection("users").doc(docId), {
                  fcmToken: admin.firestore.FieldValue.delete(),
                });
                cleaned++;
                batchHasDeletes = true;
                console.log(`[TOKEN_CLEANUP] Removed orphaned token for ${docId}`);
              }
            }
            // Otros errores (red, cuota) → omitir, no borrar
          }
        });

        if (batchHasDeletes) await firestoreBatch.commit();
      }

      console.log(`[TOKEN_CLEANUP] Done. Valid: ${valid}, Cleaned: ${cleaned}`);
    } catch (error) {
      console.error("[TOKEN_CLEANUP] Major error:", error);
    }
  }
);
