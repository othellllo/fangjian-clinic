/**
 * 登录态管理
 * token / 患者资料统一存在本地 Storage：
 *   patient_token —— 患者端独立 JWT
 *   patient_info  —— 登录接口返回的 patient 归一化结果
 */

const TOKEN_KEY = 'patient_token'
const INFO_KEY = 'patient_info'

function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

function getPatient() {
  try {
    return wx.getStorageSync(INFO_KEY) || null
  } catch (e) {
    return null
  }
}

function isLoggedIn() {
  return !!getToken()
}

function saveToken(token) {
  wx.setStorageSync(TOKEN_KEY, token || '')
}

function savePatient(patient) {
  if (patient) {
    wx.setStorageSync(INFO_KEY, patient)
  }
}

function saveAuth(token, patient) {
  if (token) saveToken(token)
  if (patient) savePatient(patient)
}

function clearAuth() {
  try {
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(INFO_KEY)
  } catch (e) {
    // 忽略清理异常
  }
  const app = getApp()
  if (app && app.globalData) {
    app.globalData.loggedIn = false
    app.globalData.patient = null
  }
}

/** 未登录时跳登录页并返回 false；已登录返回 true */
function requireLogin() {
  if (isLoggedIn()) return true
  wx.reLaunch({ url: '/pages/login/login' })
  return false
}

/** 退出登录：清本地登录态并回到登录页 */
function logout() {
  clearAuth()
  wx.reLaunch({ url: '/pages/login/login' })
}

module.exports = {
  TOKEN_KEY: TOKEN_KEY,
  INFO_KEY: INFO_KEY,
  getToken: getToken,
  getPatient: getPatient,
  isLoggedIn: isLoggedIn,
  saveToken: saveToken,
  savePatient: savePatient,
  saveAuth: saveAuth,
  clearAuth: clearAuth,
  requireLogin: requireLogin,
  logout: logout
}
