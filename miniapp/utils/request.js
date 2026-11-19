/**
 * 网络请求封装
 * - baseURL 取自 config.js
 * - 自动携带 Authorization: Bearer <patient_token>
 * - 统一处理 { code, message, data }：code !== 0 时 wx.showToast
 * - 401（HTTP 状态码 或 body.code）时清除登录态并跳转登录页
 *
 * 用法：
 *   request.get('/patient/profile', {}, { loading: true })
 *   request.post('/patient/appointments', payload, { loading: true, loadingText: '提交中' })
 */
const config = require('../config.js')
const auth = require('./auth.js')

let redirecting = false

function toast(title, duration) {
  wx.showToast({
    title: title || '操作失败',
    icon: 'none',
    duration: duration || 2200
  })
}

function isLoginPage() {
  const pages = getCurrentPages()
  if (!pages || !pages.length) return false
  const current = pages[pages.length - 1]
  const route = (current && current.route) || ''
  return route.indexOf('pages/login/login') !== -1
}

/** 登录失效：清 token 并回登录页（避免重复跳转） */
function redirectToLogin() {
  if (redirecting || isLoginPage()) return
  redirecting = true
  auth.clearAuth()
  wx.reLaunch({
    url: '/pages/login/login',
    complete: function () {
      setTimeout(function () {
        redirecting = false
      }, 1000)
    }
  })
}

function buildUrl(url) {
  if (/^https?:\/\//.test(url)) return url
  return config.BASE_URL + url
}

function makeError(code, message) {
  const text = message || '请求失败'
  const err = new Error(text)
  err.code = code
  err.message = text
  err.business = true
  return err
}

function handleResponse(res, options, resolve, reject) {
  const statusCode = res.statusCode
  const body = res.data

  // HTTP 401：登录失效
  if (statusCode === 401) {
    toast('登录已失效，请重新登录')
    redirectToLogin()
    reject(makeError(401, '登录已失效'))
    return
  }

  // 非统一响应体（例如代理错误页、空响应），2xx 视为成功
  if (!body || typeof body !== 'object' || typeof body.code === 'undefined') {
    if (statusCode >= 200 && statusCode < 300) {
      resolve(body)
      return
    }
    if (!options.silent) toast('服务异常（' + statusCode + '）')
    reject(makeError(statusCode, '服务异常'))
    return
  }

  if (Number(body.code) === 0) {
    resolve(body.data)
    return
  }

  if (Number(body.code) === 401) {
    toast(body.message || '登录已失效，请重新登录')
    redirectToLogin()
    reject(makeError(401, body.message))
    return
  }

  if (!options.silent) toast(body.message || '请求失败')
  reject(makeError(body.code, body.message))
}

function request(options) {
  const opts = options || {}
  const loading = opts.loading === true
  let loadingHidden = false

  function hideLoading() {
    if (loading && !loadingHidden) {
      loadingHidden = true
      wx.hideLoading()
    }
  }

  if (loading) {
    wx.showLoading({
      title: opts.loadingText || '加载中',
      mask: true
    })
  }

  return new Promise(function (resolve, reject) {
    const header = { 'Content-Type': 'application/json' }
    const token = auth.getToken()
    if (token) {
      header.Authorization = 'Bearer ' + token
    }
    if (opts.header) {
      Object.keys(opts.header).forEach(function (key) {
        header[key] = opts.header[key]
      })
    }

    wx.request({
      url: buildUrl(opts.url),
      method: (opts.method || 'GET').toUpperCase(),
      data: opts.data === undefined || opts.data === null ? {} : opts.data,
      header: header,
      timeout: config.REQUEST_TIMEOUT,
      success: function (res) {
        // 先关 loading 再 toast，避免 toast 被 hideLoading 一起关掉
        hideLoading()
        handleResponse(res, opts, resolve, reject)
      },
      fail: function (err) {
        hideLoading()
        if (!opts.silent) {
          toast('网络异常，请检查网络或后端服务')
        }
        reject(makeError(-1, (err && err.errMsg) || '网络异常'))
      },
      complete: function () {
        hideLoading()
      }
    })
  })
}

function withOptions(method, url, data, options) {
  const extra = options || {}
  return {
    url: url,
    method: method,
    data: data,
    loading: extra.loading === true,
    loadingText: extra.loadingText,
    silent: extra.silent === true,
    header: extra.header
  }
}

request.get = function (url, data, options) {
  return request(withOptions('GET', url, data, options))
}

request.post = function (url, data, options) {
  return request(withOptions('POST', url, data, options))
}

request.put = function (url, data, options) {
  return request(withOptions('PUT', url, data, options))
}

request.del = function (url, data, options) {
  return request(withOptions('DELETE', url, data, options))
}

request.request = request
request.redirectToLogin = redirectToLogin

module.exports = request
