export interface ApiUsageLog {
  id: string;
  userName: string;
  companyName: string;
  documentNumber?: string;
  entityType: 'PF' | 'PJ';
  timestamp: string;
  queryType: 'INITIAL' | 'UPDATE' | 'PF';
  cost: number;
}

export interface BillingSettings {
  serasaCostPerQuery: number;
  serasaPfCostPerQuery: number;
}
