import { defineStore } from 'pinia'
import { login as loginApi, getMe } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('fangjian_token') || '',
    user: JSON.parse(localStorage.getItem('fangjian_user') || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.user?.role || '',
    displayName: (state) => state.user?.realName || state.user?.username || ''
  },
  actions: {
    async login(username, password) {
      const data = await loginApi({ username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem('fangjian_token', data.token)
      localStorage.setItem('fangjian_user', JSON.stringify(data.user))
      return data.user
    },
    async refresh() {
      const user = await getMe()
      this.user = user
      localStorage.setItem('fangjian_user', JSON.stringify(user))
      return user
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem('fangjian_token')
      localStorage.removeItem('fangjian_user')
    }
  }
})
