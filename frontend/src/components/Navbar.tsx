import React from 'react';
import { Ticket, User, LogOut, LogIn, Activity } from 'lucide-react';

interface NavbarProps {
  userEmail: string | null;
  onOpenLogin: () => void;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ userEmail, onOpenLogin, onLogout }) => {
  return (
    <header className="sticky top-0 z-40 bg-white/90 backdrop-blur-md border-b border-slate-200">
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-800 text-white flex items-center justify-center shadow-lg shadow-indigo-600/30">
            <Ticket className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg font-black tracking-tight text-slate-900 flex items-center gap-2">
              TICKETING LAB
              <span className="text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                v2.0
              </span>
            </h1>
            <p className="text-[11px] text-slate-500 font-medium hidden sm:block">
              Redis ZSET 대기열 및 Redisson 분산 락 선착순 예매 시스템
            </p>
          </div>
        </div>

        {/* Status Indicator & User Info */}
        <div className="flex items-center gap-3 sm:gap-4">
          <div className="hidden md:flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 text-slate-600 text-xs font-semibold">
            <Activity className="w-3.5 h-3.5 text-emerald-500 animate-pulse" />
            <span>유량 제어: 20 TPS</span>
          </div>

          {userEmail ? (
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-indigo-50 text-indigo-900 text-xs font-bold border border-indigo-100">
                <User className="w-3.5 h-3.5 text-indigo-600" />
                <span className="max-w-[120px] sm:max-w-[180px] truncate">{userEmail}</span>
              </div>
              <button
                onClick={onLogout}
                title="로그아웃"
                className="p-2 text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-xl transition"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <button
              onClick={onOpenLogin}
              className="flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md shadow-indigo-600/20 transition"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>로그인</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
