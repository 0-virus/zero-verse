import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  apiClient,
  ApiRequestError,
  restoreSession,
  setAccessToken,
  setAuthExpiredHandler,
} from './apiClient';
import type {
  AuthTokenResponse,
  AuthUser,
  RegisterRequest,
  SigninRequest,
} from '../types/auth';

/**
 * 인증 상태(PRD §8.1).
 *
 * <p>Access Token은 {@code apiClient} 모듈 메모리에만 둔다. 여기서도 state로 복제하지 않는다 —
 * 두 곳에 있으면 갱신 시 어긋난다. localStorage·쿠키는 쓰지 않는다.
 *
 * <p>새로고침하면 메모리의 Access는 사라지지만 Refresh 쿠키는 남아 있다. 그래서 앱 시작 시
 * refresh를 한 번 시도해 세션을 복구한다 — 실패는 오류가 아니라 "비로그인"이라는 정상 상태다.
 */
export interface AuthContextValue {
  user: AuthUser | null;
  /** 초기 세션 복구가 끝나기 전. 이 동안 가드는 판단을 미룬다. */
  isLoading: boolean;
  isAuthenticated: boolean;
  signin: (request: SigninRequest) => Promise<void>;
  /** 가입 후 자동 로그인까지 시도한다. 결과로 자동 로그인 성공 여부를 알린다. */
  register: (request: RegisterRequest) => Promise<RegisterOutcome>;
  signout: () => Promise<void>;
  /** 초기 설정 완료 등으로 사용자 정보가 바뀐 뒤 다시 읽는다. */
  refreshUser: () => Promise<void>;
}

/**
 * 가입 결과.
 *
 * <p>`accountCreated`가 true인데 `signedIn`이 false면 **계정은 만들어졌지만 자동 로그인만
 * 실패한 상태**다. 이때 재가입을 유도하면 이메일 중복으로 막히므로 로그인으로 안내해야 한다
 * (ADR-0003 §4).
 */
export interface RegisterOutcome {
  accountCreated: boolean;
  signedIn: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadUser = useCallback(async () => {
    const me = await apiClient.get<AuthUser>('/api/v1/auth/me');
    setUser(me);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  // 앱 시작 시 Refresh 쿠키로 세션을 복구한다.
  //
  // `restoreSession`은 401 갱신과 같은 single-flight를 탄다. StrictMode가 effect를 두 번
  // 실행해도 rotation은 한 번만 일어난다 — 직접 호출하면 두 번째가 이미 폐기된 쿠키를 써서
  // AUTH_003을 받고 복구된 세션을 도로 비운다.
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const restored = await restoreSession();
        if (restored && !cancelled) {
          await loadUser();
        } else if (!restored && !cancelled) {
          clearSession();
        }
      } catch {
        // 쿠키가 없거나 만료됨 = 비로그인. 오류로 다루지 않는다.
        if (!cancelled) {
          clearSession();
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [loadUser, clearSession]);

  // refresh까지 실패하면 apiClient가 알려준다.
  useEffect(() => {
    setAuthExpiredHandler(() => setUser(null));
    return () => setAuthExpiredHandler(null);
  }, []);

  const signin = useCallback(
    async (request: SigninRequest) => {
      const token = await apiClient.post<AuthTokenResponse>('/api/v1/auth/signin', request);
      if (!token) {
        throw new Error('로그인 응답이 비어 있습니다.');
      }
      setAccessToken(token.accessToken);
      await loadUser();
    },
    [loadUser],
  );

  const register = useCallback(
    async (request: RegisterRequest): Promise<RegisterOutcome> => {
      // 가입 실패는 그대로 던진다 — 이메일 중복 등은 폼에서 보여줘야 한다.
      await apiClient.post<void>('/api/v1/auth/register', request);

      try {
        await signin({ email: request.email, password: request.password });
        return { accountCreated: true, signedIn: true };
      } catch (error) {
        // 계정은 이미 만들어졌다. 재가입이 아니라 로그인으로 안내해야 한다.
        if (error instanceof ApiRequestError) {
          return { accountCreated: true, signedIn: false };
        }
        return { accountCreated: true, signedIn: false };
      }
    },
    [signin],
  );

  const signout = useCallback(async () => {
    try {
      await apiClient.post<void>('/api/v1/auth/signout');
    } catch {
      // 서버 호출이 실패해도 로컬 세션은 반드시 정리한다.
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      signin,
      register,
      signout,
      refreshUser: loadUser,
    }),
    [user, isLoading, signin, register, signout, loadUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있습니다.');
  }
  return context;
}
