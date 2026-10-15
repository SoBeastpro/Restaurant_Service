// Карта OpenAPI. Сейчас живые fetch только login и logout; остальное ждёт сервер.
export const API_PREFIX = "/api/v1";

export const paths = {
  registerManager: `${API_PREFIX}/auth/register-manager`,
  login: `${API_PREFIX}/auth/login`,
  refresh: `${API_PREFIX}/auth/refresh`,
  logout: `${API_PREFIX}/auth/logout`,
  verifyEmail: `${API_PREFIX}/auth/email/verify`,
  resendEmail: `${API_PREFIX}/auth/email/resend`,
  forgotPassword: `${API_PREFIX}/auth/password/forgot`,
  resetPassword: `${API_PREFIX}/auth/password/reset`,
  changePassword: `${API_PREFIX}/auth/password/change`,
  me: `${API_PREFIX}/me`,
  myRestaurants: `${API_PREFIX}/me/restaurants`,
  myNotifications: `${API_PREFIX}/me/notifications`,
  readAllNotifications: `${API_PREFIX}/me/notifications/read-all`,
  restaurants: `${API_PREFIX}/restaurants`,
  invitation(token) {
    return `${API_PREFIX}/invitations/${encodeURIComponent(token)}`;
  },
  acceptInvitation(token) {
    return `${API_PREFIX}/invitations/${encodeURIComponent(token)}/accept`;
  },
  restaurant(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}`;
  },
  halls(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/halls`;
  },
  tables(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/tables`;
  },
  tableStatus(restaurantId, tableId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/tables/${tableId}/status-changes`;
  },
  tableHistory(restaurantId, tableId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/tables/${tableId}/status-history`;
  },
  shifts(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/shifts`;
  },
  currentShift(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/shifts/current`;
  },
  startShift(restaurantId, shiftId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/shifts/${shiftId}/start`;
  },
  closeShift(restaurantId, shiftId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/shifts/${shiftId}/close`;
  },
  shiftReport(restaurantId, shiftId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/shifts/${shiftId}/report`;
  },
  tasks(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/tasks`;
  },
  taskStatus(restaurantId, taskId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/tasks/${taskId}/status-changes`;
  },
  taskAssign(restaurantId, taskId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/tasks/${taskId}/assign`;
  },
  templates(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/task-templates`;
  },
  templateTask(restaurantId, templateId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/task-templates/${templateId}/tasks`;
  },
  invitations(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/invitations`;
  },
  employees(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/employees`;
  },
  skipSubscription(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/subscription/skip`;
  },
  statisticsOverview(restaurantId) {
    return `${API_PREFIX}/restaurants/${restaurantId}/statistics/overview`;
  },
  readNotification(notificationId) {
    return `${API_PREFIX}/me/notifications/${notificationId}/read`;
  },
};
