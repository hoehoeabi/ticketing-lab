import React, { useEffect, useState } from 'react';
import type { QueueResponseDto } from '../types';
import { queueApi } from '../api/queueApi';
import { Clock, Users, AlertTriangle, CheckCircle2, Ticket, Loader2 } from 'lucide-react';

interface WaitingRoomModalProps {
  isOpen: boolean;
  ticketId: number;
  ticketTitle: string;
  initialQueueData: QueueResponseDto | null;
  onClose: () => void;
  onProceedReservation: () => Promise<void>;
}

export const WaitingRoomModal: React.FC<WaitingRoomModalProps> = ({
  isOpen,
  ticketId,
  ticketTitle,
  initialQueueData,
  onClose,
  onProceedReservation,
}) => {
  const [queueData, setQueueData] = useState<QueueResponseDto | null>(() => initialQueueData);
  const [isReserving, setIsReserving] = useState(false);
  const [initialRank, setInitialRank] = useState<number>(() => initialQueueData?.rank || 0);

  const [prevInitialData, setPrevInitialData] = useState(initialQueueData);
  if (initialQueueData !== prevInitialData) {
    setPrevInitialData(initialQueueData);
    setQueueData(initialQueueData);
    if (initialQueueData && initialQueueData.rank > 0) {
      setInitialRank(initialQueueData.rank);
    }
  }

  // 1-second interval Polling
  useEffect(() => {
    if (!isOpen || !ticketId) return;

    const pollStatus = async () => {
      try {
        const res = await queueApi.getQueueStatus(ticketId);
        setQueueData(res.data);
      } catch (err) {
        console.error('Queue polling error:', err);
      }
    };

    const intervalId = setInterval(pollStatus, 1000);
    return () => clearInterval(intervalId);
  }, [isOpen, ticketId]);

  if (!isOpen) return null;

  const handleLeaveQueue = async () => {
    if (confirm('대기열에서 나가시겠습니까? 대기 순번이 취소됩니다.')) {
      try {
        await queueApi.leaveQueue(ticketId);
      } catch (err) {
        console.error(err);
      }
      onClose();
    }
  };

  const handleReservationClick = async () => {
    setIsReserving(true);
    try {
      await onProceedReservation();
    } finally {
      setIsReserving(false);
    }
  };

  const isActive = queueData?.status === 'ACTIVE';
  const currentRank = queueData?.rank ?? 0;
  const estimatedWaitSec = queueData?.estimatedWaitTimeSec ?? 1;

  // Calculate percentage of progress
  const progressPercent = initialRank > 0 
    ? Math.max(5, Math.min(100, Math.round(((initialRank - currentRank) / initialRank) * 100)))
    : isActive ? 100 : 15;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="bg-white rounded-3xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-100 transition-all">
        {/* Header */}
        <div className={`p-6 text-white transition-colors duration-500 ${
          isActive ? 'bg-gradient-to-r from-emerald-600 to-teal-600' : 'bg-gradient-to-r from-indigo-700 to-slate-900'
        }`}>
          <div className="flex items-center justify-between">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-white/15 backdrop-blur-sm">
              <Ticket className="w-3.5 h-3.5" /> 선착순 가상 대기실
            </span>
            <span className="text-xs text-white/70 font-mono">Ticket #{ticketId}</span>
          </div>
          <h3 className="text-xl font-black mt-2 tracking-tight text-white line-clamp-1">
            {ticketTitle}
          </h3>
        </div>

        {/* Modal Body */}
        <div className="p-7">
          {isActive ? (
            /* ACTIVE 상태 (입장 완료) */
            <div className="text-center py-4 space-y-5">
              <div className="w-20 h-20 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto shadow-inner animate-bounce">
                <CheckCircle2 className="w-12 h-12" />
              </div>

              <div>
                <h4 className="text-2xl font-black text-slate-800 tracking-tight">
                  🎉 입장 순서가 되었습니다!
                </h4>
                <p className="text-sm text-slate-600 mt-1 font-medium">
                  Active 토큰이 발급되었습니다. 5분 이내에 예매를 완료해 주세요.
                </p>
              </div>

              <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl text-xs text-emerald-800 text-left space-y-1">
                <p className="font-bold flex items-center gap-1.5">
                  🛡️ 1회성 토큰 보안 보장
                </p>
                <p className="text-emerald-700">
                  예매 완료 즉시 권한 토큰이 소모되어 다른 사용자의 연속 다회 예매를 차단합니다.
                </p>
              </div>

              <button
                onClick={handleReservationClick}
                disabled={isReserving}
                className="w-full py-4 bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-lg rounded-2xl shadow-xl shadow-emerald-600/30 transition transform hover:-translate-y-0.5 active:translate-y-0 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {isReserving ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" /> 분산 락 획득 및 예매 체결 중...
                  </>
                ) : (
                  <>
                    <Ticket className="w-5 h-5" /> 선착순 예매 확정하기 (결제)
                  </>
                )}
              </button>
            </div>
          ) : (
            /* WAITING 상태 (순번 대기 중) */
            <div className="space-y-6">
              <div className="text-center space-y-2">
                <div className="inline-flex items-center gap-2 px-3 py-1 bg-amber-50 border border-amber-200 text-amber-800 rounded-full text-xs font-bold">
                  <span className="w-2 h-2 rounded-full bg-amber-500 animate-ping" />
                  실시간 대기열 유량 제어 중 (20 TPS)
                </div>
                <h4 className="text-base text-slate-500 font-medium">
                  현재 접속량이 많아 순차적으로 입장 중입니다.
                </h4>
              </div>

              {/* Waiting Rank Card */}
              <div className="bg-slate-50 rounded-2xl p-6 border border-slate-200/80 text-center relative overflow-hidden">
                <div className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1 flex items-center justify-center gap-1.5">
                  <Users className="w-4 h-4 text-indigo-500" /> 내 앞 대기자 수
                </div>
                <div className="text-5xl font-black text-indigo-600 tracking-tight font-mono">
                  {currentRank.toLocaleString()}<span className="text-2xl font-bold text-slate-500 ml-1">명</span>
                </div>

                {/* Estimated Waiting Time */}
                <div className="mt-4 pt-3 border-t border-slate-200/60 flex items-center justify-center gap-2 text-sm text-slate-600 font-medium">
                  <Clock className="w-4 h-4 text-amber-500" />
                  <span>예상 대기 시간: 약 <strong className="text-slate-900 font-bold">{estimatedWaitSec}초</strong></span>
                </div>
              </div>

              {/* Progress Bar */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-xs font-semibold text-slate-500">
                  <span>대기열 진입</span>
                  <span>입장 완료</span>
                </div>
                <div className="w-full bg-slate-100 rounded-full h-3 overflow-hidden p-0.5 border border-slate-200">
                  <div
                    className="bg-gradient-to-r from-indigo-500 to-indigo-600 h-full rounded-full transition-all duration-700 ease-out"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
              </div>

              {/* Warning Notice */}
              <div className="p-3.5 bg-amber-50/70 border border-amber-200/60 rounded-xl flex items-start gap-2.5 text-xs text-amber-900">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <p className="leading-relaxed">
                  새로고침(F5)을 하거나 창을 닫으면 <strong>대기 순번이 초기화되어 다시 줄을 서야 합니다.</strong> 잠시만 기다려 주세요!
                </p>
              </div>

              {/* Leave Queue Button */}
              <div className="pt-2">
                <button
                  onClick={handleLeaveQueue}
                  className="w-full py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition"
                >
                  대기열 나가기 (예매 취소)
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
