import { useState, useEffect, useCallback } from 'react';
import type { TicketResponseDto, QueueResponseDto } from './types';
import { ticketApi } from './api/ticketApi';
import { queueApi } from './api/queueApi';
import { orderApi } from './api/orderApi';
import { Navbar } from './components/Navbar';
import { TicketCard } from './components/TicketCard';
import { LoginModal } from './components/LoginModal';
import { WaitingRoomModal } from './components/WaitingRoomModal';
import { Sparkles, ShieldCheck, Zap, RefreshCw, CheckCircle2 } from 'lucide-react';

export default function App() {
  const [userEmail, setUserEmail] = useState<string | null>(() => localStorage.getItem('user_email'));
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  const [tickets, setTickets] = useState<TicketResponseDto[]>([]);
  const [loadingTickets, setLoadingTickets] = useState(false);

  // Waiting Room Modal State
  const [selectedTicket, setSelectedTicket] = useState<TicketResponseDto | null>(null);
  const [isWaitingModalOpen, setIsWaitingModalOpen] = useState(false);
  const [initialQueueData, setInitialQueueData] = useState<QueueResponseDto | null>(null);

  // Success Notification
  const [successOrder, setSuccessOrder] = useState<{ orderId: number; ticketTitle: string } | null>(null);

  const fetchTickets = useCallback(async () => {
    setLoadingTickets(true);
    try {
      const res = await ticketApi.getTickets(0, 10);
      setTickets(res.data.content);
    } catch (err) {
      console.error('Failed to fetch tickets:', err);
    } finally {
      setLoadingTickets(false);
    }
  }, []);

  useEffect(() => {
    fetchTickets();
  }, [fetchTickets]);

  const handleLogout = () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('user_email');
    setUserEmail(null);
  };

  const handleEnterQueue = async (ticket: TicketResponseDto) => {
    const token = localStorage.getItem('access_token');
    if (!token) {
      setIsLoginModalOpen(true);
      return;
    }

    try {
      setSelectedTicket(ticket);
      const res = await queueApi.enterQueue(ticket.id);
      setInitialQueueData(res.data);
      setIsWaitingModalOpen(true);
    } catch (err: any) {
      const msg = err.response?.data?.message || '대기열 진입에 실패했습니다.';
      alert(`⚠️ ${msg}`);
    }
  };

  const handleProceedReservation = async () => {
    if (!selectedTicket) return;

    try {
      const res = await orderApi.createOrder(selectedTicket.id);
      setIsWaitingModalOpen(false);
      setSuccessOrder({
        orderId: res.data,
        ticketTitle: selectedTicket.title,
      });
      // Refresh tickets to reflect decremented stock
      fetchTickets();
    } catch (err: any) {
      const msg = err.response?.data?.message || '티켓 예매에 실패했습니다.';
      alert(`⚠️ 예매 실패: ${msg}`);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans text-slate-800">
      {/* Navbar */}
      <Navbar
        userEmail={userEmail}
        onOpenLogin={() => setIsLoginModalOpen(true)}
        onLogout={handleLogout}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8 space-y-10">
        {/* Success Alert Banner */}
        {successOrder && (
          <div className="p-5 bg-emerald-50 border border-emerald-200 rounded-2xl flex items-center justify-between shadow-sm animate-in fade-in duration-300">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-emerald-100 rounded-xl text-emerald-600">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <div>
                <h4 className="font-extrabold text-emerald-900 text-sm sm:text-base">
                  🎉 선착순 티켓 예매 성공! (주문번호 #{successOrder.orderId})
                </h4>
                <p className="text-xs sm:text-sm text-emerald-700 mt-0.5">
                  [{successOrder.ticketTitle}] 예매가 확정되었습니다. (Active 토큰 1회 소모 완료)
                </p>
              </div>
            </div>
            <button
              onClick={() => setSuccessOrder(null)}
              className="text-xs font-bold px-3 py-1.5 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition"
            >
              확인
            </button>
          </div>
        )}

        {/* Hero Section */}
        <section className="relative rounded-3xl bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white p-8 sm:p-12 overflow-hidden shadow-2xl border border-slate-800">
          <div className="absolute right-0 bottom-0 opacity-10 translate-x-10 translate-y-10">
            <Sparkles className="w-96 h-96" />
          </div>

          <div className="max-w-2xl relative z-10 space-y-4">
            <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-indigo-500/20 text-indigo-300 text-xs font-bold border border-indigo-500/30">
              <Zap className="w-3.5 h-3.5" /> High-Concurrency Architecture
            </div>

            <h2 className="text-3xl sm:text-5xl font-black tracking-tight leading-tight">
              Redis 가상 대기실 & <br className="hidden sm:block" />
              선착순 티켓팅 시스템
            </h2>

            <p className="text-sm sm:text-base text-slate-300 leading-relaxed">
              수십만 명의 접속 폭주 시에도 톰캣과 DB 커넥션 풀을 완벽하게 보호합니다. 
              <strong>초당 20명(20 TPS)</strong>의 유량 제어 밸브와 <strong>Redisson 분산 락</strong>으로 오버부킹 0.00%를 보장합니다.
            </p>

            {/* Feature Badges */}
            <div className="pt-2 flex flex-wrap gap-3 text-xs font-semibold text-slate-200">
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/10 backdrop-blur-sm border border-white/10">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>Zero-DB 유량 제어 (20 TPS)</span>
              </div>
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/10 backdrop-blur-sm border border-white/10">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>HTTP 계층 선제 차단 (Interceptor)</span>
              </div>
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/10 backdrop-blur-sm border border-white/10">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>Redisson 분산 락 0% 오버부킹</span>
              </div>
            </div>
          </div>
        </section>

        {/* Ticket List Section */}
        <section className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-xl font-black text-slate-900 tracking-tight">
                현재 오픈된 티켓 목록
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                원하는 티켓의 예매 버튼을 클릭하면 실시간 대기실(Waiting Room)로 진입합니다.
              </p>
            </div>

            <button
              onClick={fetchTickets}
              disabled={loadingTickets}
              className="flex items-center gap-1.5 px-3.5 py-2 bg-white hover:bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-700 shadow-sm transition disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loadingTickets ? 'animate-spin' : ''}`} />
              <span>새로고침</span>
            </button>
          </div>

          {/* Grid */}
          {tickets.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {tickets.map((ticket) => (
                <TicketCard
                  key={ticket.id}
                  ticket={ticket}
                  onEnterQueue={handleEnterQueue}
                  loading={loadingTickets}
                />
              ))}
            </div>
          ) : (
            <div className="bg-white rounded-3xl p-12 text-center border border-slate-200 space-y-3">
              <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto text-slate-400">
                <Zap className="w-6 h-6" />
              </div>
              <h4 className="font-bold text-slate-700 text-sm">등록된 티켓이 없습니다.</h4>
              <p className="text-xs text-slate-400 max-w-sm mx-auto">
                스프링 부트 백엔드 서버가 실행 중인지 확인하고, 테스트용 티켓 데이터를 생성해 주세요.
              </p>
            </div>
          )}
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 py-6 text-center text-xs text-slate-400">
        <p>© 2026 Ticketing Lab. High-Concurrency Waiting Room Architecture Demo.</p>
      </footer>

      {/* Modals */}
      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={() => setIsLoginModalOpen(false)}
        onLoginSuccess={(email) => {
          setUserEmail(email);
          setIsLoginModalOpen(false);
        }}
      />

      <WaitingRoomModal
        isOpen={isWaitingModalOpen}
        ticketId={selectedTicket?.id || 0}
        ticketTitle={selectedTicket?.title || ''}
        initialQueueData={initialQueueData}
        onClose={() => {
          setIsWaitingModalOpen(false);
          setSelectedTicket(null);
        }}
        onProceedReservation={handleProceedReservation}
      />
    </div>
  );
}
