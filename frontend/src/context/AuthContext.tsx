import React, { createContext, useContext, useEffect, useState } from 'react'
import { apiClient, BEACON_TOKEN_KEY, SESSION_ID_KEY, TOKEN_KEY } from '../api/client'

export interface UserInfo {
  id: string
  email: string
  fullName: string
  role: string
  createdAt?: string
}

interface AuthContextValue {
  user: UserInfo | null
  token: string | null
  sessionId: string | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [sessionId, setSessionId] = useState<string | null>(() => localStorage.getItem(SESSION_ID_KEY))
  const [loading, setLoading] = useState<boolean>(true)

  const refreshUser = async () => {
    const stored = localStorage.getItem(TOKEN_KEY)
    if (!stored) {
      setUser(null)
      setToken(null)
      setLoading(false)
      return
    }
    try {
      const res = await apiClient.get<UserInfo>('/me')
      setUser(res.data)
      setToken(stored)
    } catch {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(SESSION_ID_KEY)
      localStorage.removeItem(BEACON_TOKEN_KEY)
      setUser(null)
      setToken(null)
      setSessionId(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refreshUser()
  }, [])

  // Sprint 7: Heartbeat every 60 seconds while tab is visible + navigator.sendBeacon on pagehide
  useEffect(() => {
    if (!token || !sessionId) return

    const sendHeartbeat = () => {
      if (document.visibilityState === 'visible') {
        apiClient.post(`/sessions/${sessionId}/heartbeat`).catch(() => {})
      }
    }

    sendHeartbeat()
    const interval = window.setInterval(sendHeartbeat, 60_000)

    const onPageHide = () => {
      const beaconToken = localStorage.getItem(BEACON_TOKEN_KEY)
      if (!beaconToken || !navigator.sendBeacon) return
      const payload = JSON.stringify({ beaconToken })
      const blob = new Blob([payload], { type: 'application/json' })
      navigator.sendBeacon(`/api/sessions/${sessionId}/checkout-beacon`, blob)
    }

    window.addEventListener('pagehide', onPageHide)
    return () => {
      window.clearInterval(interval)
      window.removeEventListener('pagehide', onPageHide)
    }
  }, [token, sessionId])

  const login = async (email: string, password: string) => {
    const res = await apiClient.post<{
      token: string
      sessionId?: string
      beaconToken?: string
      user: UserInfo
    }>('/auth/login', {
      email,
      password,
    })
    localStorage.setItem(TOKEN_KEY, res.data.token)
    if (res.data.sessionId) {
      localStorage.setItem(SESSION_ID_KEY, res.data.sessionId)
      setSessionId(res.data.sessionId)
    }
    if (res.data.beaconToken) {
      localStorage.setItem(BEACON_TOKEN_KEY, res.data.beaconToken)
    }
    setToken(res.data.token)
    setUser(res.data.user)
  }

  const register = async (email: string, password: string, fullName: string) => {
    const res = await apiClient.post<{
      token: string
      sessionId?: string
      beaconToken?: string
      user: UserInfo
    }>('/auth/register', {
      email,
      password,
      fullName,
    })
    localStorage.setItem(TOKEN_KEY, res.data.token)
    if (res.data.sessionId) {
      localStorage.setItem(SESSION_ID_KEY, res.data.sessionId)
      setSessionId(res.data.sessionId)
    }
    if (res.data.beaconToken) {
      localStorage.setItem(BEACON_TOKEN_KEY, res.data.beaconToken)
    }
    setToken(res.data.token)
    setUser(res.data.user)
  }

  const logout = async () => {
    const currentSessionId = localStorage.getItem(SESSION_ID_KEY)
    try {
      if (currentSessionId) {
        await apiClient.post(`/sessions/${currentSessionId}/checkout`, { reason: 'LOGOUT' })
      }
      await apiClient.post('/auth/logout')
    } catch {
      // Ignore network errors on logout
    } finally {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(SESSION_ID_KEY)
      localStorage.removeItem(BEACON_TOKEN_KEY)
      setToken(null)
      setSessionId(null)
      setUser(null)
    }
  }

  return (
    <AuthContext.Provider
      value={{ user, token, sessionId, loading, login, register, logout, refreshUser }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}

