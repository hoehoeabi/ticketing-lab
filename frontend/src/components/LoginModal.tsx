import React, { useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { X, LogIn, UserPlus, KeyRound, Send, Timer, CheckCircle2, ShieldCheck } from 'lucide-react';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: (email: string) => void;
}

const formatTime = (totalSec: number) => {
  const m = String(Math.floor(totalSec / 60)).padStart(2, '0');
  const s = String(totalSec % 60).padStart(2, '0');
  return `${m}:${s}`;
};

const getErrorMessage = (err: any, fallback: string): string =>
  err?.response?.data?.message || fallback;

export const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onLoginSuccess }) => {
  const [isSignUp, setIsSignUp] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [infoMsg, setInfoMsg] = useState('');
  const [loading, setLoading] = useState(false);

  // ===== 이메일 인증 상태 (회원가입 전용) =====
  const [codeSent, setCodeSent] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [verificationCode, setVerificationCode] = useState('');
  const [verifyingCode, setVerifyingCode] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [remainingSec, setRemainingSec] = useState(0);

  // 인증번호 유효 시간 카운트다운 (서버가 내려준 TTL 기준)
  useEffect(() => {
    if (expiresAt === null || emailVerified) return;

    const timerId = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
      setRemainingSec(left);
      if (left === 0) window.clearInterval(timerId);
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [expiresAt, emailVerified]);

  if (!isOpen) return null;

  const isCodeExpired = codeSent && !emailVerified && remainingSec === 0;

  const resetVerification = () => {
    setCodeSent(false);
    setSendingCode(false);
    setVerificationCode('');
    setVerifyingCode(false);
    setEmailVerified(false);
    setExpiresAt(null);
    setRemainingSec(0);
  };

  const resetMessages = () => {
    setErrorMsg('');
    setInfoMsg('');
  };

  const handleClose = () => {
    resetVerification();
    resetMessages();
    onClose();
  };

  const handleEmailChange = (value: string) => {
    setEmail(value);
    // 인증번호는 발송된 이메일에 귀속되므로, 이메일이 바뀌면 인증 단계를 초기화
    if (codeSent) {
      resetVerification();
      setInfoMsg('');
    }
  };

  const handleSendCode = async () => {
    resetMessages();
    if (!email) {
      setErrorMsg('이메일 주소를 입력해 주세요.');
      return;
    }

    setSendingCode(true);
    try {
      const res = await authApi.sendEmailCode(email);
      const ttlSec = res.data.expiresInSeconds;
      setExpiresAt(Date.now() + ttlSec * 1000);
      setRemainingSec(ttlSec);
      setCodeSent(true);
      setVerificationCode('');
      setInfoMsg(res.message);
    } catch (err: any) {
      setErrorMsg(getErrorMessage(err, '인증번호 발송에 실패했습니다.'));
    } finally {
      setSendingCode(false);
    }
  };

  const handleVerifyCode = async () => {
    resetMessages();
    if (verificationCode.length !== 6) {
      setErrorMsg('6자리 인증번호를 입력해 주세요.');
      return;
    }

    setVerifyingCode(true);
    try {
      await authApi.verifyEmailCode(email, verificationCode);
      setEmailVerified(true);
      setInfoMsg('이메일 인증이 완료되었습니다. 10분 이내에 회원가입을 완료해 주세요.');
    } catch (err: any) {
      setErrorMsg(getErrorMessage(err, '인증번호 확인에 실패했습니다.'));
      setVerificationCode('');
    } finally {
      setVerifyingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();

    if (isSignUp && !emailVerified) {
      setErrorMsg('이메일 인증을 먼저 완료해 주세요.');
      return;
    }

    setLoading(true);

    try {
      if (isSignUp) {
        await authApi.signup(email, password);
        // After signup, automatically login
        const loginRes = await authApi.login(email, password);
        localStorage.setItem('access_token', loginRes.data.accessToken);
        localStorage.setItem('user_email', email);
        onLoginSuccess(email);
        handleClose();
      } else {
        const res = await authApi.login(email, password);
        localStorage.setItem('access_token', res.data.accessToken);
        localStorage.setItem('user_email', email);
        onLoginSuccess(email);
        handleClose();
      }
    } catch (err: any) {
      const msg = getErrorMessage(err, isSignUp ? '회원가입에 실패했습니다.' : '로그인에 실패했습니다.');
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickLogin = (quickEmail: string) => {
    setEmail(quickEmail);
    setPassword('password123!');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-100">
        {/* Header */}
        <div className="bg-slate-900 text-white p-6 relative">
          <button
            onClick={handleClose}
            className="absolute right-5 top-5 text-slate-400 hover:text-white transition"
          >
            <X className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-indigo-500/20 rounded-xl text-indigo-400">
              <KeyRound className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-xl font-bold">
                {isSignUp ? '티켓팅 랩 회원가입' : '티켓팅 랩 로그인'}
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">
                {isSignUp
                  ? '이메일 인증 후 가입할 수 있습니다.'
                  : '대기열 진입 및 티켓 예매를 위해 로그인이 필요합니다.'}
              </p>
            </div>
          </div>
        </div>

        {/* Form Body */}
        <div className="p-6">
          {errorMsg && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-600 rounded-xl text-sm font-medium">
              ⚠️ {errorMsg}
            </div>
          )}
          {infoMsg && !errorMsg && (
            <div className="mb-4 p-3 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-xl text-sm font-medium">
              ✉️ {infoMsg}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                이메일 주소
              </label>
              <div className="flex gap-2">
                <input
                  type="email"
                  required
                  value={email}
                  readOnly={isSignUp && emailVerified}
                  onChange={(e) => (isSignUp ? handleEmailChange(e.target.value) : setEmail(e.target.value))}
                  placeholder="example@test.com"
                  className={`flex-1 min-w-0 px-4 py-2.5 rounded-xl border focus:outline-none focus:ring-2 text-sm ${
                    isSignUp && emailVerified
                      ? 'border-emerald-300 bg-emerald-50/50 text-slate-500 focus:ring-emerald-400 cursor-not-allowed'
                      : 'border-slate-200 focus:ring-indigo-500'
                  }`}
                />
                {isSignUp &&
                  (emailVerified ? (
                    <span className="shrink-0 inline-flex items-center gap-1 px-3 rounded-xl bg-emerald-100 text-emerald-700 text-xs font-bold">
                      <CheckCircle2 className="w-4 h-4" /> 인증 완료
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={handleSendCode}
                      disabled={sendingCode || !email}
                      className="shrink-0 inline-flex items-center gap-1.5 px-3 rounded-xl bg-slate-900 hover:bg-slate-700 text-white text-xs font-semibold transition disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <Send className="w-3.5 h-3.5" />
                      {sendingCode ? '발송 중...' : codeSent ? '재발송' : '인증번호 발송'}
                    </button>
                  ))}
              </div>
            </div>

            {/* Email Verification Code Step (Signup only) */}
            {isSignUp && codeSent && !emailVerified && (
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                <div className="flex items-center justify-between mb-2">
                  <label className="flex items-center gap-1 text-xs font-semibold text-slate-600">
                    <ShieldCheck className="w-3.5 h-3.5" /> 인증번호 6자리
                  </label>
                  <span
                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold tabular-nums ${
                      isCodeExpired
                        ? 'bg-red-100 text-red-600'
                        : remainingSec <= 60
                          ? 'bg-amber-100 text-amber-700'
                          : 'bg-indigo-100 text-indigo-700'
                    }`}
                  >
                    <Timer className="w-3.5 h-3.5" />
                    {isCodeExpired ? '만료됨' : formatTime(remainingSec)}
                  </span>
                </div>
                <div className="flex gap-2">
                  <input
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    value={verificationCode}
                    disabled={isCodeExpired}
                    onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    onKeyDown={(e) => {
                      // Enter 키가 회원가입 폼 제출이 아닌 인증 확인으로 동작하도록 처리
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        if (!verifyingCode && !isCodeExpired) handleVerifyCode();
                      }
                    }}
                    placeholder="000000"
                    className="flex-1 min-w-0 px-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm tracking-[0.4em] font-semibold disabled:bg-slate-100"
                  />
                  <button
                    type="button"
                    onClick={handleVerifyCode}
                    disabled={verifyingCode || isCodeExpired || verificationCode.length !== 6}
                    className="shrink-0 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {verifyingCode ? '확인 중...' : '인증 확인'}
                  </button>
                </div>
                <p className={`mt-2 text-[11px] ${isCodeExpired ? 'text-red-500' : 'text-slate-400'}`}>
                  {isCodeExpired
                    ? '인증 시간이 만료되었습니다. [재발송]을 눌러 새 인증번호를 받아 주세요.'
                    : '메일이 오지 않았다면 스팸함을 확인하거나 재발송해 주세요.'}
                </p>
              </div>
            )}

            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                비밀번호
              </label>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
              />
            </div>

            <button
              type="submit"
              disabled={loading || (isSignUp && !emailVerified)}
              className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl shadow-lg shadow-indigo-500/25 transition flex items-center justify-center gap-2 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? (
                '처리 중...'
              ) : isSignUp ? (
                <>
                  <UserPlus className="w-4 h-4" /> {emailVerified ? '회원가입 후 로그인' : '이메일 인증 후 가입 가능'}
                </>
              ) : (
                <>
                  <LogIn className="w-4 h-4" /> 로그인
                </>
              )}
            </button>
          </form>

          {/* Quick Login Helper (테스트 계정은 이미 가입되어 있으므로 로그인 모드에서만 노출) */}
          {!isSignUp && (
            <div className="mt-6 pt-5 border-t border-slate-100">
              <p className="text-xs font-semibold text-slate-500 mb-2">⚡ 빠른 테스트용 계정 입력</p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => handleQuickLogin('user1@test.com')}
                  className="flex-1 py-1.5 px-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-medium transition"
                >
                  user1@test.com
                </button>
                <button
                  type="button"
                  onClick={() => handleQuickLogin('user2@test.com')}
                  className="flex-1 py-1.5 px-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-medium transition"
                >
                  user2@test.com
                </button>
              </div>
            </div>
          )}

          {/* Toggle between Signup / Login */}
          <div className="mt-4 text-center">
            <button
              type="button"
              onClick={() => {
                setIsSignUp(!isSignUp);
                resetVerification();
                resetMessages();
              }}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-semibold"
            >
              {isSignUp ? '이미 계정이 있으신가요? 로그인하기' : '계정이 없으신가요? 이메일 인증 후 회원가입'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
