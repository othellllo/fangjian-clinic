import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const service = axios.create({
  baseURL: '/api',
  timeout: 20000
})

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('fangjian_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data
    // 兼容非统一响应体的接口（文件下载等）
    if (body === undefined || body === null || typeof body !== 'object' || !('code' in body)) {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 401) {
      localStorage.removeItem('fangjian_token')
      localStorage.removeItem('fangjian_user')
      router.replace('/login')
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('fangjian_token')
      localStorage.removeItem('fangjian_user')
      router.replace('/login')
      ElMessage.error('登录已失效，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default service
