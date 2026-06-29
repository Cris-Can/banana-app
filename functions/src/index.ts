import * as admin from "firebase-admin";


admin.initializeApp();

export { onNotificationCreated, cleanupOrphanedTokens } from "./notifications";
export { onRatingCreated } from "./rating-triggers";
export {
  onEventCreatedNotifyZone,
  onEventRatableReminder,
  onFeedPostCreated,
  onParticipantApproved,
  onEventCreatedValidation
} from "./event-triggers";
export { 
  onUserCreated, 
  redeemFounderCode, 
  validateAndGrantPurchase,
  syncEmailVerificationOnSignIn,
  scheduledEmailVerificationSync 
} from "./user-lifecycle";
export { cleanupUsersDatabase, createPromoCode, createCampaign, toggleCampaignActive } from "./admin-functions";
export { checkRateLimitGuard, scheduledRateLimitCleanup, recordProfileView } from "./security-functions";
export { geocodeExternalLocation } from "./external-event-geocoder";
export { setupExternalEventBot, addExternalSource, removeExternalSource, listExternalSources } from "./external-event-sources";
export { scheduledCheckExternalSources, runSchedulerNow } from "./external-event-scheduler";
export { publishExternalEvent, listPendingExternalEvents, approveExternalEvent, rejectExternalEvent, processInstagramUrl } from "./external-event-admin-workflow";
export { listCreatedExternalEvents, deleteExternalEvent } from "./external-event-admin-workflow";


/**
 * =====================================================
 * 🤝 SISTEMA DE AMISTADES V2 - CANÓNICO
 * =====================================================
 * Se ha migrado a V2 para garantizar la integridad de datos
 * y escalabilidad. Las funciones V1 han sido deprecadas.
 */

import {
  sendFriendRequestV2,
  acceptFriendRequestV2,
  rejectFriendRequestV2,
  removeFriendV2,
  verifySocialSystemConsistency,
  repairUserCounters,
  getDiscoverySuggestionsV2
} from "./socialSystemV2";

export {
  sendFriendRequestV2,
  acceptFriendRequestV2,
  rejectFriendRequestV2,
  removeFriendV2,
  verifySocialSystemConsistency,
  repairUserCounters,
  getDiscoverySuggestionsV2
};
