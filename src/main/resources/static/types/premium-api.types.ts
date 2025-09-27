/**
 * 🎯 TypeScript Type Definitions for Premium API
 * Generated from Java DTOs for Frontend type safety
 */

// ===== ENUMS =====
export type TrialStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'NOT_STARTED';
export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'PENDING';
export type UrgencyLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'EXPIRED';
export type PlanType = 'trial' | 'monthly' | 'quarterly' | 'yearly' | 'none';

// ===== MAIN API RESPONSE TYPES =====

/**
 * 🏆 Premium Status Response - GET /api/premium/status
 */
export interface PremiumStatusResponse {
  isPremium: boolean;
  subscriptionStatus: string;
  planType: string;
  daysRemaining: number;
  expiryDate: string; // ISO datetime string
  premiumBadgeUrl: string | null;
  isExpired: boolean;
  message: string;
  trial: TrialInfo;
  availablePlans: AvailablePlans;
}

export interface TrialInfo {
  status: TrialStatus;
  hasAccess: boolean;
  startDate: string; // ISO datetime string
  endDate: string; // ISO datetime string
  daysRemaining: number;
  hoursRemaining: number;
  daysOverdue: number;
  message: string;
}

export interface AvailablePlans {
  monthly: PlanDetails;
  quarterly: PlanDetails;
  yearly: PlanDetails;
}

export interface PlanDetails {
  price: number;
  duration: string;
  displayName: string;
}

/**
 * 📊 Trial Analytics Response - GET /api/premium/trial/analytics
 */
export interface TrialAnalyticsResponse {
  trialStatus: TrialStatus;
  hasAccess: boolean;
  analytics: TrialAnalytics;
  dates: DateInfo;
  message: string;
}

export interface TrialAnalytics {
  totalTrialDays: number; // Always 14
  daysUsed: number;
  daysRemaining: number;
  hoursRemaining: number;
  progressPercentage: number; // 0-100
  timeRemainingText: string; // "3 ngày 12 giờ" or "Đã hết hạn"
  urgencyLevel: UrgencyLevel;
  shouldShowUpgradePrompt: boolean; // true when <= 3 days
}

export interface DateInfo {
  startDate: string; // ISO datetime string
  endDate: string; // ISO datetime string
  currentDate: string; // ISO datetime string
}

/**
 * 🎁 Start Trial Response - POST /api/premium/start-trial
 */
export interface StartTrialResponse {
  success: boolean;
  trialStatus: TrialStatus;
  message: string;
  isPremium: boolean;
  planType: string; // "trial"
  startDate: string; // ISO datetime string
  endDate: string; // ISO datetime string
  daysRemaining: number;
  premiumBadgeUrl: string | null;
}

/**
 * 🏆 Cancel Subscription Response - POST /api/premium/cancel
 */
export interface CancelSubscriptionResponse {
  success: boolean;
  message: string;
  type: 'trial_cancelled' | 'subscription_cancelled';
  immediately: boolean;
}

/**
 * 📊 Subscription Statistics Response - GET /api/premium/admin/statistics
 */
export interface SubscriptionStatisticsResponse {
  success: boolean;
  statistics: Statistics;
}

export interface Statistics {
  totalActiveSubscriptions: number;
  totalUsers: number;
  monthlyRevenue: number;
  yearlyRevenue: number;
  trialStatistics: TrialStatistics;
}

export interface TrialStatistics {
  activeTrials: number;
  expiredTrials: number;
  trialsExpiringSoon: number; // <= 3 days
  trialConversionRate: number; // Percentage
}

// ===== API SERVICE TYPES =====

/**
 * 🎯 Premium API Service Interface
 */
export interface PremiumApiService {
  // Get current premium status with trial info
  getPremiumStatus(): Promise<PremiumStatusResponse>;

  // Get detailed trial analytics for countdown UI
  getTrialAnalytics(): Promise<TrialAnalyticsResponse>;

  // Start 14-day trial
  startTrial(): Promise<StartTrialResponse>;

  // Cancel subscription/trial
  cancelSubscription(immediately?: boolean): Promise<CancelSubscriptionResponse>;

  // Admin: Get subscription statistics
  getSubscriptionStatistics(): Promise<SubscriptionStatisticsResponse>;
}

// ===== UTILITY TYPES =====

/**
 * 🎨 UI Component Props Types
 */
export interface TrialCountdownProps {
  daysRemaining: number;
  hoursRemaining: number;
  urgencyLevel: UrgencyLevel;
  timeRemainingText: string;
  shouldShowUpgradePrompt: boolean;
  progressPercentage: number;
}

export interface PremiumBadgeProps {
  isPremium: boolean;
  badgeUrl: string | null;
  planType: PlanType;
}

export interface UpgradePromptProps {
  show: boolean;
  urgencyLevel: UrgencyLevel;
  daysRemaining: number;
  availablePlans: AvailablePlans;
}

// ===== API ENDPOINTS CONSTANTS =====
export const PREMIUM_API_ENDPOINTS = {
  STATUS: '/api/premium/status',
  ANALYTICS: '/api/premium/trial/analytics',
  START_TRIAL: '/api/premium/start-trial',
  CANCEL: '/api/premium/cancel',
  ADMIN_STATS: '/api/premium/admin/statistics'
} as const;
