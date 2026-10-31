import React, { createContext, useContext, useEffect, useState } from 'react'
import { apiClient, TOKEN_KEY } from '../api/client'

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
      setUser(null)
      setToken(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refreshUser()
  }, [])

  const login = async (email: string, password: string) => {
    const res = await apiClient.post<{ token: string; user: UserInfo }>('/auth/login', {
      email,
      password,
    })
    localStorage.setItem(TOKEN_KEY, res.data.token)
    setToken(res.data.token)
    setUser(res.data.user)
  }

  const register = async (email: string, password: string, fullName: string) => {
    const res = await apiClient.post<{ token: string; user: UserInfo }>('/auth/register', {
      email,
      password,
      fullName,
    })
    localStorage.setItem(TOKEN_KEY, res.data.token)
    setToken(res.data.token)
    setUser(res.data.user)
  }

  const logout = async () => {
    try {
      await apiClient.post('/auth/logout')
    } catch {
      // Ignore network errors on logout
    } finally {
      localStorage.removeItem(TOKEN_KEY)
      setToken(null)
      setUser(null)
    }
  }

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout, refreshUser }}>
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

