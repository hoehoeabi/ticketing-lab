import React, { useState } from 'react';
import { authApi } from '../api/authApi';
import { X, LogIn, UserPlus, KeyRound } from 'lucide-react';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: (email: string) => void;
}

export const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onLoginSuccess }) => {
  const [isSignUp, setIsSignUp] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    setLoading(true);

    try {
      if (isSignUp) {
        await authApi.signup(email, password);
        // After signup, automatically login
        const loginRes = await authApi.login(email, password);
        localStorage.setItem('access_token', loginRes.data.accessToken);
        localStorage.setItem('user_email', email);
        onLoginSuccess(email);
        onClose();
      } else {
        const res = await authApi.login(email, password);
        localStorage.setItem('access_token', res.data.accessToken);
        localStorage.setItem('user_email', email);
        onLoginSuccess(email);
        onClose();
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || (isSignUp ? '회원가입에 실패했습니다.' : '로그인에 실패했습니다.');
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
            onClick={onClose}
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
                대기열 진입 및 티켓 예매를 위해 로그인이 필요합니다.
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

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                이메일 주소
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="example@test.com"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
              />
            </div>

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
              disabled={loading}
              className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl shadow-lg shadow-indigo-500/25 transition flex items-center justify-center gap-2 text-sm disabled:opacity-50"
            >
              {loading ? (
                '처리 중...'
              ) : isSignUp ? (
                <>
                  <UserPlus className="w-4 h-4" /> 회원가입 후 로그인
                </>
              ) : (
                <>
                  <LogIn className="w-4 h-4" /> 로그인
                </>
              )}
            </button>
          </form>

          {/* Quick Login Helper */}
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

          {/* Toggle between Signup / Login */}
          <div className="mt-4 text-center">
            <button
              type="button"
              onClick={() => {
                setIsSignUp(!isSignUp);
                setErrorMsg('');
              }}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-semibold"
            >
              {isSignUp ? '이미 계정이 있으신가요? 로그인하기' : '계정이 없으신가요? 3초 회원가입'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
