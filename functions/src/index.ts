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
export { onUserCreated, redeemFounderCode, validateAndGrantPurchase } from "./user-lifecycle";
export { cleanupUsersDatabase, createPromoCode, createCampaign, toggleCampaignActive } from "./admin-functions";
export { checkRateLimitGuard, validatePasswordStrength, scheduledRateLimitCleanup, recordProfileView } from "./security-functions";


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
