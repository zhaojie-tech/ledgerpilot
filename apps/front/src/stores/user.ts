import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type LoginForm, type LoginResponse } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('ledgerpilot_token') || '')
  const userInfo = ref<LoginResponse['user'] | null>(null)

  async function login(form: LoginForm) {
    const res = await authApi.login(form)
    token.value = res.data.token
    userInfo.value = res.data.user
    localStorage.setItem('ledgerpilot_token', res.data.token)
  }

  async function register(form: any) {
    const res = await authApi.register(form)
    token.value = res.data.token
    userInfo.value = res.data.user
    localStorage.setItem('ledgerpilot_token', res.data.token)
  }

  async function fetchUserInfo() {
    try {
      const res = await authApi.getCurrentUser()
      userInfo.value = res.data
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('ledgerpilot_token')
    router.push('/login')
  }

  function isLoggedIn() {
    return !!token.value
  }

  return { token, userInfo, login, register, fetchUserInfo, logout, isLoggedIn }
})
