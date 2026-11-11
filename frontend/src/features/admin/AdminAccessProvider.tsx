import {
  createContext,
  useCallback,
  useContext,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { ApiRequestError } from '../../lib/apiClient';
import { useOptionalAuth } from '../../lib/authContext';
import { adminApi } from './adminApi';

export type AdminAccessStatus = 'idle' | 'loading' | 'allowed' | 'denied' | 'error';
export type AdminErrorBoundary = 'permission' | 'target' | 'transient';

export interface AdminAccessValue {
  status: AdminAccessStatus;
  isLoading: boolean;
  error: unknown | null;
  generation: number;
  viewerId: number | null;
  probe: () => Promise<boolean>;
  retry: () => Promise<boolean>;
  invalidate: (error?: unknown) => void;
  isCurrent: (generation: number, viewerId?: number | null) => boolean;
  revalidateAfterError: (error: unknown) => Promise<AdminErrorBoundary>;
}

const AdminAccessContext = createContext<AdminAccessValue | null>(null);

export function isAdminPermissionError(error: unknown): boolean {
  return (
    error instanceof ApiRequestError &&
    (error.code === 'ADMIN_001' || error.code === 'USER_003' || error.code === 'AUTH_002' || error.code === 'AUTH_004')
  );
}

export function isAdminActorNotFound(error: unknown): boolean {
  return error instanceof ApiRequestError && error.code === 'USER_001';
}

export function isAdminTargetNotFound(error: unknown): boolean {
  return isAdminActorNotFound(error);
}

function isAuthBoundaryError(error: unknown): boolean {
  return isAdminPermissionError(error) || isAdminActorNotFound(error);
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  if (error instanceof Error) return error.message;
  return '관리자 권한을 확인하지 못했습니다.';
}

function authKeyFor(
  isLoading: boolean,
  isAuthenticated: boolean,
  sessionExpired: boolean,
  viewerId: number | null,
): string {
  if (isLoading) return 'loading';
  if (sessionExpired) return 'expired';
  if (isAuthenticated && viewerId !== null) return `viewer:${viewerId}`;
  return 'guest';
}

/** 현재 DB의 ADMIN 권한을 목록 size=1 probe로 확인한다(M9 A-v2). */
export function AdminAccessProvider({ children }: { children: ReactNode }) {
  const auth = useOptionalAuth();
  const isAuthLoading = auth?.isLoading ?? false;
  const isAuthenticated = Boolean(auth?.isAuthenticated && auth.user && !auth.sessionExpired);
  const viewerId = auth?.user?.id ?? null;
  const authKey = authKeyFor(isAuthLoading, isAuthenticated, auth?.sessionExpired ?? false, viewerId);
  const [status, setStatus] = useState<AdminAccessStatus>('idle');
  const [error, setError] = useState<unknown | null>(null);
  const accountGeneration = useRef(0);
  const requestGeneration = useRef(0);
  const sessionGeneration = useRef(0);
  const accountKeyRef = useRef(authKey);
  const viewerIdRef = useRef(viewerId);
  const inFlight = useRef<{ key: string; promise: Promise<boolean> } | null>(null);

  useLayoutEffect(() => {
    accountGeneration.current += 1;
    requestGeneration.current += 1;
    sessionGeneration.current += 1;
    accountKeyRef.current = authKey;
    viewerIdRef.current = viewerId;
    inFlight.current = null;
    setStatus('idle');
    setError(null);
  }, [authKey, viewerId]);

  const invalidate = useCallback((nextError?: unknown) => {
    requestGeneration.current += 1;
    sessionGeneration.current += 1;
    inFlight.current = null;
    setStatus('denied');
    setError(nextError ?? null);
  }, []);

  const probe = useCallback(async (): Promise<boolean> => {
    if (isAuthLoading || !isAuthenticated || viewerId === null) return false;
    const key = `${accountGeneration.current}:${viewerId}`;
    if (inFlight.current?.key === key) return inFlight.current.promise;
    const snapshot = {
      account: accountGeneration.current,
      request: ++requestGeneration.current,
      viewerId,
    };

    setStatus('loading');
    setError(null);
    const promise = (async () => {
      try {
        await adminApi.list({ page: 0, size: 1 });
        if (
          snapshot.account !== accountGeneration.current ||
          snapshot.request !== requestGeneration.current ||
          snapshot.viewerId !== viewerIdRef.current ||
          accountKeyRef.current !== authKey
        ) {
          return false;
        }
        setStatus('allowed');
        setError(null);
        return true;
      } catch (caught) {
        if (
          snapshot.account !== accountGeneration.current ||
          snapshot.request !== requestGeneration.current ||
          snapshot.viewerId !== viewerIdRef.current
        ) {
          return false;
        }
        setStatus(isAuthBoundaryError(caught) ? 'denied' : 'error');
        setError(caught);
        return false;
      } finally {
        if (inFlight.current?.key === key) inFlight.current = null;
      }
    })();
    inFlight.current = { key, promise };
    return promise;
  }, [authKey, isAuthLoading, isAuthenticated, viewerId]);

  const retry = useCallback(() => probe(), [probe]);

  const isCurrent = useCallback((generation: number, expectedViewerId: number | null = viewerId): boolean => {
    return generation === sessionGeneration.current && expectedViewerId === viewerIdRef.current;
  }, [viewerId]);

  const revalidateAfterError = useCallback(
    async (caught: unknown): Promise<AdminErrorBoundary> => {
      if (isAdminPermissionError(caught)) {
        invalidate(caught);
        return 'permission';
      }
      if (!isAdminActorNotFound(caught)) return 'transient';
      const current = await probe();
      if (current) return 'target';
      return 'permission';
    },
    [invalidate, probe],
  );

  const value = useMemo<AdminAccessValue>(
    () => ({
      status,
      isLoading: isAuthLoading || status === 'idle' || status === 'loading',
      error,
      generation: sessionGeneration.current,
      viewerId,
      probe,
      retry,
      invalidate,
      isCurrent,
      revalidateAfterError,
    }),
    [error, invalidate, isAuthLoading, isCurrent, isAuthenticated, probe, retry, revalidateAfterError, status, viewerId],
  );

  return <AdminAccessContext.Provider value={value}>{children}</AdminAccessContext.Provider>;
}

export function useAdminAccess(): AdminAccessValue {
  const context = useContext(AdminAccessContext);
  if (!context) throw new Error('useAdminAccess는 AdminAccessProvider 안에서만 사용할 수 있습니다.');
  return context;
}

export function useOptionalAdminAccess(): AdminAccessValue | null {
  return useContext(AdminAccessContext);
}

export function adminAccessErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'ADMIN_001') return '현재 관리자 권한이 없습니다.';
    if (error.code === 'USER_003') return '정지된 계정은 관리자 화면을 사용할 수 없습니다.';
    if (error.code === 'USER_001') return '현재 로그인 계정을 확인할 수 없습니다. 다시 접속해 주세요.';
    if (error.code === 'AUTH_002' || error.code === 'AUTH_004') return '로그인이 만료되었습니다. 다시 접속해 주세요.';
    return error.message || '관리자 권한을 확인하지 못했습니다.';
  }
  return errorMessage(error) || '관리자 권한을 확인하지 못했습니다.';
}
