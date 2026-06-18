export interface CompanyNoteAttachment {
  id?: string | null;
  fileName?: string | null;
  contentType?: string | null;
  fileSize?: number | null;
}

export interface CompanyNote {
  id: string;
  content: string;
  authorName: string;
  authorEmail: string;
  createdAt: string;
  canDelete: boolean;
  repliedToId?: string | null;
  repliedToAuthorName?: string | null;
  repliedToContent?: string | null;
  hasAttachment: boolean;
  attachmentFileName?: string | null;
  attachmentContentType?: string | null;
  attachmentSize?: number | null;
  attachments?: CompanyNoteAttachment[];
}
