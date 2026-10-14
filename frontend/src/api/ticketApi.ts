import { apiClient } from './client';
import type { RsData, TicketResponseDto, PageResponse } from '../types';

export const ticketApi = {
  getTickets: async (page = 0, size = 10): Promise<RsData<PageResponse<TicketResponseDto>>> => {
    const response = await apiClient.get<RsData<PageResponse<TicketResponseDto>>>(
      `/tickets?page=${page}&size=${size}`
    );
    return response.data;
  },

  getTicket: async (ticketId: number): Promise<RsData<TicketResponseDto>> => {
    const response = await apiClient.get<RsData<TicketResponseDto>>(`/tickets/${ticketId}`);
    return response.data;
  },
};
