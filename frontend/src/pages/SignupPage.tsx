import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  AuthCard,
  AuthCardBody,
  AuthCardFooter,
  AuthFormError,
  AuthNotice,
  AuthSubmitButton,
} from '../features/auth/AuthCard';
import { FormField } from '../components/ui/FormField';
import { ApiRequestError } from '../lib/apiClient';
import { useAuth } from '../lib/authContext';
import { ERROR_CODE } from '../types/auth';

/**
 * 회원가입(FR-AUTH-01).
 *
 * <p>`name`·`birthDate` 모두 필수다(PRD §9.4-AA).
 *
 * <p>비밀번호 안내는 정본 카피("영문+숫자 조합")가 아니라 **실제 검증 규칙**을 쓴다. 특수문자가
 * 필수인데 안내에 없으면 사용자가 계속 실패한다 — 저장·검증 요구가 시각 카피보다 우선하는
 * 경계다(AGENTS.md 소스 오브 트루스 §1·2 경계). 정본처럼 placeholder로만 알리고, 힌트 자리는
 * 서버가 준 필드 오류에만 쓴다(같은 문구를 두 번 보여주지 않는다).
 *
 * <p>가입 후 자동 로그인을 시도하되, **자동 로그인만 실패**하면 계정이 이미 만들어졌음을
 * 알리고 로그인으로 안내한다. 재가입을 유도하면 이메일 중복으로 막힌다(ADR-0003 §4).
 */
export function SignupPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    nickname: '',
    name: '',
    email: '',
    password: '',
    birthDate: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (key: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setNotice(null);
    setSubmitting(true);

    try {
      const outcome = await register(form);

      if (outcome.signedIn) {
        // 가입 직후에는 초기 설정이 남아 있다.
        navigate('/blog/setup', { replace: true });
        return;
      }
      // 계정은 생겼다. 재가입이 아니라 로그인으로 보낸다.
      setNotice('계정이 생성되었습니다. 로그인해 주세요.');
    } catch (caught) {
      applyError(caught, setError, setFieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard activeTab="signup">
      <form onSubmit={handleSubmit} noValidate>
        <AuthCardBody>
          <AuthFormError message={error} />
          <AuthNotice message={notice} />

          <FormField
            label="닉네임"
            value={form.nickname}
            onChange={update('nickname')}
            placeholder="2~20자, 유니버스에서 불릴 이름"
            hint={fieldErrors.nickname}
            autoComplete="nickname"
            required
          />
          <FormField
            label="이름"
            value={form.name}
            onChange={update('name')}
            placeholder="실명을 입력하세요"
            hint={fieldErrors.name}
            autoComplete="name"
            required
          />
          <FormField
            label="이메일"
            type="email"
            value={form.email}
            onChange={update('email')}
            placeholder="you@universe.dev"
            hint={fieldErrors.email}
            autoComplete="email"
            required
          />
          <FormField
            label="비밀번호"
            type="password"
            value={form.password}
            onChange={update('password')}
            placeholder="8자 이상, 영문·숫자·특수문자 포함"
            hint={fieldErrors.password}
            autoComplete="new-password"
            required
          />
          <FormField
            label="생년월일"
            type="date"
            value={form.birthDate}
            onChange={update('birthDate')}
            hint={fieldErrors.birthDate}
            autoComplete="bday"
            required
          />

          <AuthSubmitButton disabled={submitting}>
            {submitting ? '만드는 중…' : '나의 별 만들기 ✦'}
          </AuthSubmitButton>
        </AuthCardBody>
      </form>

      <AuthCardFooter>
        이미 계정이 있나요?{' '}
        <Link to="/signin" className="font-bold text-accent">
          로그인
        </Link>
      </AuthCardFooter>
    </AuthCard>
  );
}

function applyError(
  caught: unknown,
  setError: (message: string) => void,
  setFieldErrors: (errors: Record<string, string>) => void,
): void {
  if (!(caught instanceof ApiRequestError)) {
    setError('가입에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    return;
  }

  switch (caught.code) {
    case ERROR_CODE.EMAIL_TAKEN:
      setFieldErrors({ email: '이미 사용 중인 이메일입니다.' });
      setError('이미 사용 중인 이메일입니다.');
      return;
    case ERROR_CODE.NICKNAME_TAKEN:
      setFieldErrors({ nickname: '이미 사용 중인 닉네임입니다.' });
      setError('이미 사용 중인 닉네임입니다.');
      return;
    case ERROR_CODE.VALIDATION: {
      // 서버가 준 필드별 사유를 그대로 쓴다. 프론트가 문구를 새로 만들지 않는다.
      const errors: Record<string, string> = {};
      for (const detail of caught.details) {
        errors[detail.field] = detail.reason;
      }
      setFieldErrors(errors);
      setError('입력값을 확인해 주세요.');
      return;
    }
    default:
      setError(caught.message);
  }
}
