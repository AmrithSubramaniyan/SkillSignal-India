import axios from 'axios'

export const TOKEN_KEY = 'skillsignal_jwt_token'

export const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      const path = window.location.pathname
      if (path !== '/login' && path !== '/register' && path !== '/reset-password') {
        localStorage.removeItem(TOKEN_KEY)
      }
    }
    return Promise.reject(error)
  },
)

