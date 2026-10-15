import { apiClient } from './client';
import type { RsData, QueueResponseDto } from '../types';

export const queueApi = {
  enterQueue: async (ticketId: number): Promise<RsData<QueueResponseDto>> => {
    const response = await apiClient.post<RsData<QueueResponseDto>>(
      `/queue/tickets/${ticketId}/enter`
    );
    return response.data;
  },

  getQueueStatus: async (ticketId: number): Promise<RsData<QueueResponseDto>> => {
    const response = await apiClient.get<RsData<QueueResponseDto>>(
      `/queue/tickets/${ticketId}/status`
    );
    return response.data;
  },

  leaveQueue: async (ticketId: number): Promise<RsData<void>> => {
    const response = await apiClient.delete<RsData<void>>(
      `/queue/tickets/${ticketId}/leave`
    );
    return response.data;
  },
};
