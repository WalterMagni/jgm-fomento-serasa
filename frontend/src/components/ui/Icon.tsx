"use client";

import {
  Archive, ArchiveRestore, ArrowDown, ArrowLeft, ArrowLeftRight, ArrowRight, ArrowUp, ArrowUpDown,
  BadgeCheck, BarChart3, Braces, Brain, Briefcase, Building2, Calendar, CalendarDays, Check, CheckCheck, CheckCircle2, ChevronDown,
  ChevronLeft, ChevronRight, ChevronUp, ChevronsDownUp, ChevronsLeft, ChevronsRight, ChevronsUpDown,
  CircleAlert, CircleX, ClipboardCheck, Clock, Copy, Database, Download, Eye, ExternalLink, File, FileArchive,
  FileText, FileUp, Filter, Folder, FolderOpen, FolderPlus, Footprints, Gavel, Globe, HelpCircle,
  History, Home, Hourglass, IdCard, Image as ImageIcon, Inbox, Info, Landmark, Layers, Link as LinkIcon, Mail, MessageSquarePlus, Network,
  ListChecks, LogOut, Map as MapIcon, Maximize2, Minus, Moon, MoreHorizontal, MoreVertical,
  Paperclip, Pencil, PenLine, Phone, Plus, Presentation, ReceiptText, Reply, RotateCcw, Save, ScrollText, Search,
  Settings, Shield, ShieldAlert, ShieldCheck, Sparkles, StickyNote, Store, Sun, Table, Tag, ThumbsUp, Trash2,
  TrendingUp, Undo2, Unlink, Upload, UploadCloud, User, UserCheck, Users, UserSearch, Wallet, X, ZoomIn,
  type LucideIcon,
} from "lucide-react";

// Padrão de ícones do sistema: nomes semânticos (compatíveis com os antigos Material) → Lucide.
const ICONS: Record<string, LucideIcon> = {
  // navegação / setas
  close: X, clear: X, chevron_right: ChevronRight, chevron_left: ChevronLeft, expand_more: ChevronDown,
  expand_less: ChevronUp, keyboard_double_arrow_right: ChevronsRight, keyboard_double_arrow_left: ChevronsLeft,
  unfold_more: ChevronsUpDown, unfold_less: ChevronsDownUp, arrow_back: ArrowLeft, arrow_forward: ArrowRight,
  arrow_upward: ArrowUp, arrow_downward: ArrowDown, more_vert: MoreVertical, more_horiz: MoreHorizontal,
  // ações
  search: Search, manage_search: Search, refresh: RotateCcw, sync: RotateCcw, restore: RotateCcw,
  delete: Trash2, edit: Pencil, edit_note: PenLine, drive_file_rename_outline: PenLine, save: Save,
  add: Plus, remove: Minus, content_copy: Copy, file_copy: Copy, download: Download, upload_file: FileUp,
  cloud_upload: UploadCloud, open_in_new: ExternalLink, open_in_full: Maximize2, zoom_in: ZoomIn,
  zoom_out_map: Maximize2, sort: ArrowUpDown, filter_alt: Filter, done_all: CheckCheck, check: Check,
  checklist: ListChecks, undo: Undo2, reply: Reply, logout: LogOut, link: LinkIcon, link_off: Unlink,
  visibility: Eye, settings: Settings, more: MoreHorizontal, upload: Upload, layers_clear: Layers,
  // status / feedback
  check_circle: CheckCircle2, how_to_reg: UserCheck, warning: CircleAlert, warning_amber: CircleAlert,
  error: CircleAlert, error_outline: CircleAlert, priority_high: CircleAlert, info: Info,
  hourglass_empty: Hourglass, schedule: Clock, lock_clock: Clock, fact_check: ClipboardCheck,
  thumb_up: ThumbsUp, security_update_warning: ShieldAlert, security: ShieldCheck, shield: Shield,
  // entidades / dados
  business: Building2, domain: Building2, add_business: Building2, person: User, people: Users,
  person_search: UserSearch, attach_file: Paperclip, folder: Folder, folder_open: FolderOpen,
  create_new_folder: FolderPlus, inventory_2: Archive, unarchive: ArchiveRestore, history: History,
  history_edu: ScrollText, receipt_long: ReceiptText, description: FileText, article: FileText,
  insert_drive_file: File, folder_zip: FileArchive, picture_as_pdf: FileText, table_chart: Table,
  data_object: Braces, gavel: Gavel, event: Calendar, tag: Tag, home: Home, inbox: Inbox, phone: Phone,
  // mapa / análise
  map: MapIcon, travel_explore: Globe, language: Globe, streetview: Eye, directions_walk: Footprints,
  alt_route: ArrowLeftRight, swap_horiz: ArrowLeftRight, auto_awesome: Sparkles, psychology: Brain,
  insights: TrendingUp, analytics: BarChart3, leaderboard: BarChart3, stacked_bar_chart: BarChart3,
  // tema
  dark_mode: Moon, light_mode: Sun,
  // arquivos genéricos
  image: ImageIcon,
  // extras (rollout app-wide)
  account_balance: Landmark, account_tree: Network, add_comment: MessageSquarePlus, autorenew: RotateCcw,
  badge: IdCard, bar_chart: BarChart3, business_center: Briefcase, calendar_today: CalendarDays,
  folder_shared: FolderOpen, hourglass_top: Hourglass, mail: Mail, rule: ListChecks, verified_user: BadgeCheck,
  public: Globe, account_balance_wallet: Wallet, trending_up: TrendingUp, store: Store,
  cancel: CircleX, database: Database, domain_verification: BadgeCheck, notes: StickyNote, pending: Clock,
  slideshow: Presentation,
};

type Props = {
  name: string;
  size?: number;
  className?: string;
  strokeWidth?: number;
} & Omit<React.SVGProps<SVGSVGElement>, "ref" | "name" | "size">;

/** Ícone padrão do sistema (Lucide). Use `name` semântico; fallback discreto se não mapeado. */
export default function Icon({ name, size = 18, className, strokeWidth = 2, ...rest }: Props) {
  const Cmp = ICONS[name] ?? HelpCircle;
  return <Cmp size={size} className={className} strokeWidth={strokeWidth} aria-hidden {...rest} />;
}
