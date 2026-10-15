import React from 'react';
import type { TicketResponseDto } from '../types';
import { Calendar, Users, Flame, Sparkles } from 'lucide-react';

interface TicketCardProps {
  ticket: TicketResponseDto;
  onEnterQueue: (ticket: TicketResponseDto) => void;
  loading: boolean;
}

export const TicketCard: React.FC<TicketCardProps> = ({ ticket, onEnterQueue, loading }) => {
  const isSoldOut = ticket.remainingQuantity <= 0;
  const stockRatio = ticket.totalQuantity > 0 
    ? Math.round((ticket.remainingQuantity / ticket.totalQuantity) * 100)
    : 0;

  return (
    <div className="bg-white rounded-3xl shadow-sm hover:shadow-xl transition-all duration-300 border border-slate-200/80 overflow-hidden flex flex-col justify-between group">
      <div>
        {/* Ticket Header Graphic */}
        <div className="h-32 bg-gradient-to-tr from-slate-900 via-indigo-950 to-indigo-800 p-5 text-white flex flex-col justify-between relative overflow-hidden">
          <div className="absolute right-0 top-0 opacity-10 translate-x-4 -translate-y-4">
            <Sparkles className="w-36 h-36" />
          </div>

          <div className="flex items-center justify-between z-10">
            <span className="text-[11px] font-bold tracking-wider uppercase px-2.5 py-0.5 rounded-full bg-white/20 backdrop-blur-sm">
              선착순 오픈
            </span>
            <span className={`text-xs font-bold px-2.5 py-0.5 rounded-full ${
              isSoldOut ? 'bg-red-500/80 text-white' : 'bg-emerald-500/80 text-white'
            }`}>
              {isSoldOut ? '매진 (SOLD OUT)' : '예매 가능'}
            </span>
          </div>

          <div className="z-10">
            <h3 className="text-xl font-black tracking-tight text-white group-hover:text-indigo-200 transition">
              {ticket.title}
            </h3>
          </div>
        </div>

        {/* Content Body */}
        <div className="p-5 space-y-4">
          {/* Remaining Quantity Bar */}
          <div className="space-y-1.5">
            <div className="flex justify-between text-xs">
              <span className="text-slate-500 font-medium flex items-center gap-1">
                <Flame className="w-3.5 h-3.5 text-orange-500" /> 잔여 수량
              </span>
              <span className="font-bold text-slate-800 font-mono">
                {ticket.remainingQuantity.toLocaleString()} / {ticket.totalQuantity.toLocaleString()} 장
              </span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  stockRatio < 20 ? 'bg-red-500' : stockRatio < 50 ? 'bg-amber-500' : 'bg-indigo-600'
                }`}
                style={{ width: `${stockRatio}%` }}
              />
            </div>
          </div>

          {/* Metadata */}
          <div className="space-y-2 pt-2 border-t border-slate-100 text-xs text-slate-600">
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-slate-400" />
              <span>오픈 일시: {new Date(ticket.openAt).toLocaleString('ko-KR')}</span>
            </div>
            <div className="flex items-center gap-2">
              <Users className="w-4 h-4 text-slate-400" />
              <span>동시성 제어: Redisson 분산 락 + Redis 대기열 (20 TPS)</span>
            </div>
          </div>
        </div>
      </div>

      {/* Button Footer */}
      <div className="p-5 pt-0">
        <button
          onClick={() => onEnterQueue(ticket)}
          disabled={isSoldOut || loading}
          className={`w-full py-3.5 rounded-2xl font-bold text-sm shadow-md transition flex items-center justify-center gap-2 ${
            isSoldOut
              ? 'bg-slate-100 text-slate-400 cursor-not-allowed'
              : 'bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white shadow-indigo-500/20 hover:-translate-y-0.5'
          }`}
        >
          {isSoldOut ? '매진되었습니다' : '선착순 예매하기 (대기열 진입)'}
        </button>
      </div>
    </div>
  );
};
