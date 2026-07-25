import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  AuthCard,
  AuthCardBody,
  AuthCardFooter,
  AuthFormError,
  AuthSubmitButton,
} from '../features/auth/AuthCard';
import { FormField } from '../components/ui/FormField';
import { ApiRequestError } from '../lib/apiClient';
import { useAuth } from '../lib/authContext';
import { ERROR_CODE } from '../types/auth';

/**
 * 로그인(FR-AUTH-02).
 *
 * <p>실패 사유를 세분화하지 않는다 — 서버가 이메일 없음과 비밀번호 불일치를 같은
 * `AUTH_001`로 주는 이유(계정 존재 여부 비노출)를 화면에서 무너뜨리지 않는다.
 */
export function SigninPage() {
  const { signin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await signin({ email, password });
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
      navigate(from ?? '/', { replace: true });
    } catch (caught) {
      setError(toMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard activeTab="signin">
      <form onSubmit={handleSubmit} noValidate>
        <AuthCardBody>
          <AuthFormError message={error} />

          <FormField
            label="이메일"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@universe.dev"
            autoComplete="email"
            required
          />
          <FormField
            label="비밀번호"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호를 입력하세요"
            autoComplete="current-password"
            required
          />

          <AuthSubmitButton disabled={submitting}>
            {submitting ? '접속 중…' : '접속하기 ✦'}
          </AuthSubmitButton>
        </AuthCardBody>
      </form>

      <AuthCardFooter>
        아직 계정이 없나요?{' '}
        <Link to="/signup" className="font-bold text-accent">
          회원가입
        </Link>
      </AuthCardFooter>
    </AuthCard>
  );
}

function toMessage(caught: unknown): string {
  if (!(caught instanceof ApiRequestError)) {
    return '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (caught.code === ERROR_CODE.SUSPENDED) {
    return '정지된 계정입니다. 관리자에게 문의해 주세요.';
  }
  // AUTH_001과 그 외 모두 동일한 문구로 보여준다.
  return '이메일 또는 비밀번호가 올바르지 않습니다.';
}
