import { onDocumentCreated } from "firebase-functions/v2/firestore";


import * as admin from "firebase-admin";

admin.initializeApp();

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

    const userSnap = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();

    if (!userSnap.exists) {
      console.log("Usuario no existe:", userId);
      return;
    }

    const fcmToken = userSnap.data()?.fcmToken;

    if (!fcmToken) {
      console.log("Usuario sin token FCM:", userId);
      return;
    }

    const message = {
      token: fcmToken,
      notification: {
        title: notification.title,
        body: notification.message,
      },
      data: {
        eventId: notification.eventId ?? "",
        type: notification.type ?? "GENERIC",
      },
    };

    await admin.messaging().send(message);
    console.log("Push enviado a:", userId);
  }
);

/**
 * =====================================================
 * 🆕 A14.2 + A14.3 — EVENTOS POR COMUNA
 * - Flag global ON/OFF
 * - Preferencia por usuario
 * =====================================================
 */
export const onEventCreatedNotifyCommune = onDocumentCreated(
  "events/{eventId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    // ---------- FLAG GLOBAL ----------
    const configSnap = await admin
      .firestore()
      .collection("app_config")
      .doc("notifications")
      .get();

    const notifyEnabled =
      configSnap.exists &&
      configSnap.data()?.notifyEventsByCommune === true;

    if (!notifyEnabled) {
      console.log("Notificaciones por comuna DESACTIVADAS (flag global)");
      return;
    }

    // ---------- EVENT DATA ----------
    const eventData = snapshot.data();
    const eventId = event.params.eventId;
    const title = eventData.title;
    const commune = eventData.commune;
    const creatorId = eventData.creatorId;

    if (!commune) {
      console.log("Evento sin comuna, no se notifica:", eventId);
      return;
    }

    console.log(`Evento nuevo en ${commune}. Buscando usuarios...`);

    // ---------- USERS QUERY ----------
    const usersSnapshot = await admin
      .firestore()
      .collection("users")
      .where("commune", "==", commune)
      .get();

    if (usersSnapshot.empty) {
      console.log("No hay usuarios en la comuna:", commune);
      return;
    }

    const batch = admin.firestore().batch();
    let notificationsCreated = 0;

    usersSnapshot.forEach((doc) => {
      const user = doc.data();
      const userId = user.uid;

      // ❌ No notificar al creador
      if (userId === creatorId) return;

      // ❌ Usuario sin token
      if (!user.fcmToken) return;

      // 🔔 Preferencia por usuario (A14.3)
      if (user.notifyEventsByCommune !== true) return;

      const notificationRef = admin
        .firestore()
        .collection("notifications")
        .doc();

      batch.set(notificationRef, {
        userId: userId,
        title: "Nuevo evento cerca de ti 📍",
        message: `Se creó un nuevo evento en tu comuna: ${title}`,
        eventId: eventId,
        createdAt: Date.now(),
        read: false,
        type: "EVENT_CREATED",
      });

      notificationsCreated++;
    });

    if (notificationsCreated === 0) {
      console.log(
        "No se crearon notificaciones (nadie opt-in) para evento:",
        eventId
      );
      return;
    }

    await batch.commit();

    console.log(
      `Notificaciones creadas (${notificationsCreated}) para evento ${eventId}`
    );
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
      await db.collection("users").doc(toUserId).update({
        ratingSum: sum,
        ratingCount: count,
      });

      console.log(`Profile updated for ${toUserId}`);
    } catch (error) {
      console.error("Error updating user rating stats:", error);
    }
  }
);
