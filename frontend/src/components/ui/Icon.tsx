"use client";

import {
  ArchiveIcon, ArrowBendUpLeftIcon, ArrowClockwiseIcon, ArrowCounterClockwiseIcon, ArrowDownIcon, ArrowLeftIcon,
  ArrowRightIcon, ArrowSquareOutIcon, ArrowUUpLeftIcon, ArrowUpIcon, ArrowsClockwiseIcon, ArrowsDownUpIcon,
  ArrowsInLineVerticalIcon, ArrowsLeftRightIcon, ArrowsOutIcon, AtIcon, BankIcon, BellIcon, BellSlashIcon, BookOpenIcon,
  BracketsCurlyIcon, BrainIcon, BriefcaseIcon, BuildingsIcon, CalendarBlankIcon, CalendarIcon, CaretDoubleLeftIcon,
  CaretDoubleRightIcon, CaretDownIcon, CaretLeftIcon, CaretRightIcon, CaretUpDownIcon, CaretUpIcon,
  ChartBarIcon, ChatCenteredTextIcon, ChatCircleIcon, CheckCircleIcon, CheckIcon, ChecksIcon, CircleIcon, CircleNotchIcon,
  ClockCounterClockwiseIcon, ClockIcon, CloudArrowUpIcon, CopyIcon, DatabaseIcon, DotsThreeIcon,
  DotsThreeVerticalIcon, DownloadSimpleIcon, EnvelopeIcon, EyeIcon, EyeSlashIcon, FileArrowUpIcon, FileIcon,
  FilePdfIcon, FileTextIcon, FileZipIcon, FlagIcon, FloppyDiskIcon, FolderIcon, FolderOpenIcon, FolderPlusIcon,
  FolderUserIcon, FunnelIcon, FunnelSimpleIcon, GavelIcon, GearSixIcon, GlobeIcon, HandshakeIcon, HourglassHighIcon,
  HourglassIcon, HouseIcon, IdentificationBadgeIcon, IdentificationCardIcon, ImageIcon, InfoIcon, LightbulbIcon,
  LinkBreakIcon, LinkSimpleIcon, ListChecksIcon, ListIcon, LockIcon, MagnifyingGlassIcon, MagnifyingGlassMinusIcon,
  MagnifyingGlassPlusIcon, MapTrifoldIcon, MegaphoneIcon, MinusCircleIcon, MinusIcon, MoonIcon, NoteIcon,
  PaletteIcon, PaperclipIcon, PencilLineIcon, PencilSimpleIcon, PersonSimpleWalkIcon, PhoneIcon, PlusCircleIcon,
  PlusIcon, PresentationIcon, ProhibitIcon, QuestionIcon, ReceiptIcon, ScrollIcon, SealCheckIcon, ShieldCheckIcon,
  ShieldIcon, ShieldWarningIcon, SignOutIcon, SparkleIcon, StackIcon, StorefrontIcon, SunIcon, TableIcon, TagIcon,
  ThumbsUpIcon, TrashIcon, TrayIcon, TreeStructureIcon, TrendUpIcon, UploadSimpleIcon, UserCheckIcon, UserIcon,
  UsersIcon, WalletIcon, WarningCircleIcon, XCircleIcon, XIcon,
  ScalesIcon, ListBulletsIcon, HouseLineIcon, MapPinIcon, CreditCardIcon, ChartLineUpIcon, WarningOctagonIcon,
  InvoiceIcon, ChartLineIcon, TrophyIcon,
  type Icon as PhosphorIcon, type IconWeight,
} from "@phosphor-icons/react";

// Padrão de ícones do sistema: nomes semânticos (compatíveis com os antigos Material) → Phosphor.
const ICONS: Record<string, PhosphorIcon> = {
  // navegação / setas
  close: XIcon, clear: XIcon, chevron_right: CaretRightIcon, chevron_left: CaretLeftIcon, expand_more: CaretDownIcon,
  expand_less: CaretUpIcon, keyboard_double_arrow_right: CaretDoubleRightIcon, keyboard_double_arrow_left: CaretDoubleLeftIcon,
  menu: ListIcon, search_off: MagnifyingGlassMinusIcon, lock: LockIcon,
  unfold_more: CaretUpDownIcon, unfold_less: ArrowsInLineVerticalIcon, arrow_back: ArrowLeftIcon, arrow_forward: ArrowRightIcon,
  arrow_upward: ArrowUpIcon, arrow_downward: ArrowDownIcon, more_vert: DotsThreeVerticalIcon, more_horiz: DotsThreeIcon,
  // ações
  search: MagnifyingGlassIcon, manage_search: MagnifyingGlassIcon, refresh: ArrowCounterClockwiseIcon,
  sync: ArrowsClockwiseIcon, restore: ArrowCounterClockwiseIcon,
  delete: TrashIcon, edit: PencilSimpleIcon, edit_note: PencilLineIcon, drive_file_rename_outline: PencilLineIcon, save: FloppyDiskIcon,
  add: PlusIcon, remove: MinusIcon, content_copy: CopyIcon, file_copy: CopyIcon, download: DownloadSimpleIcon, upload_file: FileArrowUpIcon,
  cloud_upload: CloudArrowUpIcon, open_in_new: ArrowSquareOutIcon, open_in_full: ArrowsOutIcon, zoom_in: MagnifyingGlassPlusIcon,
  zoom_out_map: ArrowsOutIcon, sort: ArrowsDownUpIcon, filter_alt: FunnelSimpleIcon, done_all: ChecksIcon, check: CheckIcon,
  checklist: ListChecksIcon, undo: ArrowUUpLeftIcon, reply: ArrowBendUpLeftIcon, logout: SignOutIcon, link: LinkSimpleIcon,
  link_off: LinkBreakIcon, visibility: EyeIcon, settings: GearSixIcon, more: DotsThreeIcon, upload: UploadSimpleIcon,
  layers_clear: StackIcon, progress_activity: CircleNotchIcon,
  // status / feedback
  check_circle: CheckCircleIcon, radio_button_unchecked: CircleIcon, how_to_reg: UserCheckIcon, warning: WarningCircleIcon, warning_amber: WarningCircleIcon,
  error: WarningCircleIcon, error_outline: WarningCircleIcon, priority_high: WarningCircleIcon, info: InfoIcon,
  hourglass_empty: HourglassIcon, schedule: ClockIcon, lock_clock: ClockIcon, fact_check: SealCheckIcon,
  thumb_up: ThumbsUpIcon, security_update_warning: ShieldWarningIcon, security: ShieldCheckIcon, shield: ShieldIcon,
  // entidades / dados
  business: BuildingsIcon, domain: BuildingsIcon, add_business: BuildingsIcon, person: UserIcon, people: UsersIcon,
  groups: UsersIcon, handshake: HandshakeIcon, lightbulb: LightbulbIcon,
  person_search: IdentificationCardIcon, attach_file: PaperclipIcon, folder: FolderIcon, folder_open: FolderOpenIcon,
  create_new_folder: FolderPlusIcon, inventory_2: ArchiveIcon, unarchive: ArrowUUpLeftIcon, history: ClockCounterClockwiseIcon,
  history_edu: ScrollIcon, receipt_long: ReceiptIcon, description: FileTextIcon, article: FileTextIcon,
  insert_drive_file: FileIcon, folder_zip: FileZipIcon, picture_as_pdf: FilePdfIcon, table_chart: TableIcon,
  data_object: BracketsCurlyIcon, gavel: GavelIcon, event: CalendarIcon, tag: TagIcon, home: HouseIcon, inbox: TrayIcon, phone: PhoneIcon,
  // mapa / análise
  map: MapTrifoldIcon, travel_explore: GlobeIcon, language: GlobeIcon, streetview: EyeIcon, directions_walk: PersonSimpleWalkIcon,
  alt_route: ArrowsLeftRightIcon, swap_horiz: ArrowsLeftRightIcon, auto_awesome: SparkleIcon, psychology: BrainIcon,
  insights: TrendUpIcon, analytics: ChartBarIcon, leaderboard: ChartBarIcon, stacked_bar_chart: ChartBarIcon,
  // tema
  dark_mode: MoonIcon, light_mode: SunIcon,
  // arquivos genéricos
  image: ImageIcon,
  // extras (rollout app-wide)
  account_balance: BankIcon, account_tree: TreeStructureIcon, add_comment: ChatCenteredTextIcon, autorenew: ArrowsClockwiseIcon,
  badge: IdentificationBadgeIcon, bar_chart: ChartBarIcon, business_center: StorefrontIcon, calendar_today: CalendarBlankIcon,
  folder_shared: BriefcaseIcon, hourglass_top: HourglassHighIcon, mail: EnvelopeIcon, rule: ListChecksIcon, verified_user: ShieldCheckIcon,
  public: GlobeIcon, account_balance_wallet: WalletIcon, trending_up: TrendUpIcon, store: StorefrontIcon,
  cancel: XCircleIcon, database: DatabaseIcon, domain_verification: SealCheckIcon, notes: NoteIcon, pending: ClockIcon,
  slideshow: PresentationIcon,
  // esteira de prospecção
  conveyor_belt: FunnelIcon, task_alt: CheckCircleIcon, folder_supervised: FolderUserIcon, flag: FlagIcon,
  block: ProhibitIcon, visibility_off: EyeSlashIcon, notifications: BellIcon, notifications_off: BellSlashIcon, campaign: MegaphoneIcon,
  add_circle: PlusCircleIcon, remove_circle: MinusCircleIcon, restart_alt: ArrowClockwiseIcon,
  help: QuestionIcon, help_outline: QuestionIcon, menu_book: BookOpenIcon,
  palette: PaletteIcon, contract: ScrollIcon, verified: SealCheckIcon, do_not_disturb_on: MinusCircleIcon, storefront: StorefrontIcon,
  // esteira de liberação
  chat: ChatCircleIcon, alternate_email: AtIcon,
  // análise de PF, Visão Cedente e Padrões (antes caíam no "?")
  balance: ScalesIcon, format_list_bulleted: ListBulletsIcon, home_work: HouseLineIcon, location_on: MapPinIcon,
  payment: CreditCardIcon, query_stats: ChartLineUpIcon, report: WarningOctagonIcon, request_quote: InvoiceIcon,
  timeline: ChartLineIcon,
  // nota da equipe
  emoji_events: TrophyIcon,
};

/**
 * Ícones que comunicam situação (ok, alerta, erro). A camada de fundo segue a cor do próprio
 * ícone em vez do laranja da marca, para não misturar "atenção" com "decoração".
 */
const SITUACAO = new Set([
  "check_circle", "task_alt", "warning", "warning_amber", "error", "error_outline", "priority_high", "cancel",
  "info", "how_to_reg", "verified", "verified_user", "domain_verification", "security_update_warning",
  "thumb_up", "help", "help_outline", "block",
]);

/**
 * Glifos de traço puro (setas, fechar, check, mais). No duotone o caret vira triângulo cheio e o
 * "+" ganha um quadrado de fundo, então eles ficam no peso `bold`, que equivale ao traço antigo.
 */
const TRACO = new Set([
  "chevron_right", "chevron_left", "expand_more", "expand_less", "keyboard_double_arrow_right",
  "keyboard_double_arrow_left", "arrow_back", "arrow_forward", "arrow_upward", "arrow_downward",
  "unfold_more", "unfold_less", "close", "clear", "check", "done_all", "add", "remove", "more_vert",
  "more_horiz", "more", "menu", "sort", "undo", "reply", "refresh", "sync", "restore", "autorenew",
  "restart_alt", "progress_activity",
]);

type Props = {
  name: string;
  size?: number;
  className?: string;
  /** Legado do Lucide: traço 2.5+ vira peso `bold`. */
  strokeWidth?: number;
  weight?: IconWeight;
} & Omit<React.SVGProps<SVGSVGElement>, "ref" | "name" | "size" | "weight">;

/**
 * Ícone padrão do sistema (Phosphor duotone). Use `name` semântico; fallback discreto se não mapeado.
 *
 * <p>Duotone por padrão, com a camada de fundo pintada pelo CSS (`.icone-duo` em globals.css).
 * Abaixo de 13px o duotone vira borrão, então o peso cai para `bold`, assim como os checks
 * que antes pediam traço grosso e os glifos de TRACO.</p>
 */
export default function Icon({ name, size = 18, className, strokeWidth, weight, ...rest }: Props) {
  const Cmp = ICONS[name] ?? QuestionIcon;
  const peso: IconWeight = weight ?? ((strokeWidth ?? 0) >= 2.5 || size <= 12 || TRACO.has(name) ? "bold" : "duotone");
  return (
    <Cmp
      size={size}
      weight={peso}
      className={`icone-duo ${className ?? ""}`}
      data-tom={SITUACAO.has(name) ? "situacao" : undefined}
      aria-hidden
      {...rest}
    />
  );
}
