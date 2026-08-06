import client from './client'
import type { ApiResponse } from '@/types'

export interface MenuPermission {
  role: string
  menuKey: string
  actionKey: string
  enabled: boolean
}

export interface MenuPermissionUpsertPayload {
  role: string
  menuKey: string
  actionKey: string
  enabled: boolean
}

// 로그인한 본인 역할의 예외 설정만 내려온다. 목록에 없는 (menuKey, actionKey) 조합은 기본 true(표시/활성화)로 간주한다.
export const getMyMenuPermissions = () =>
  client.get<ApiResponse<MenuPermission[]>>('/menu-permissions/my')

export const getMenuPermissionsByRole = (role: string) =>
  client.get<ApiResponse<MenuPermission[]>>('/menu-permissions', { params: { role } })

export const upsertMenuPermission = (payload: MenuPermissionUpsertPayload) =>
  client.put<ApiResponse<MenuPermission>>('/menu-permissions', payload)
