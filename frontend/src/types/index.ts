export interface RsData<T> {
  code: string;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type QueueStatus = 'WAITING' | 'ACTIVE' | 'NOT_IN_QUEUE';

export interface QueueResponseDto {
  userId: number;
  ticketId: number;
  status: QueueStatus;
  rank: number;
  estimatedWaitTimeSec: number;
}

export interface TicketResponseDto {
  id: number;
  title: string;
  totalQuantity: number;
  remainingQuantity: number;
  openAt: string;
  createdAt: string;
}

export interface LoginResponseDto {
  accessToken: string;
}

export interface EmailSendResponseDto {
  expiresInSeconds: number;
}

export interface UserInfo {
  email: string;
  isLoggedIn: boolean;
}
