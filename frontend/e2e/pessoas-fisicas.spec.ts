import { expect, test, type Page } from "@playwright/test";

/**
 * Fumaça da lista de Pessoas Físicas: filtrar, ordenar, a URL guardar o estado e a gaveta abrir
 * e fechar. O resumo é interceptado no navegador com dados fixos — o teste não depende de quem
 * está no banco nem gasta consulta Serasa.
 *
 * Variáveis (iguais às da esteira):
 *   E2E_EMAIL / E2E_SENHA  usuário do teste (padrão: o admin do DatabaseSeeder)
 */
const EMAIL = process.env.E2E_EMAIL ?? "admin@jgm.com.br";
const SENHA = process.env.E2E_SENHA ?? "admin123";

const hoje = new Date().toISOString().slice(0, 19);
const antiga = new Date(Date.now() - 200 * 86_400_000).toISOString().slice(0, 19);

const PESSOAS = [
  {
    cpf: "11111111111", nome: "ANA LIMPA SILVA", cidade: "AMERICANA", uf: "SP", consultaEm: hoje,
    situacao: "LIMPO", totalOcorrencias: 0, dividaTotal: 0, restricoes: [], vinculos: [],
  },
  {
    cpf: "22222222222", nome: "BRUNO RESTRICAO SOUZA", cidade: "CAMPINAS", uf: "SP", consultaEm: hoje,
    situacao: "COM_RESTRICAO", totalOcorrencias: 1, dividaTotal: 1200,
    restricoes: [{ tipo: "PEFIN", quantidade: 1, valor: 1200 }], vinculos: [],
  },
  {
    cpf: "33333333333", nome: "CARLA CRITICA LIMA", cidade: "LIMEIRA", uf: "SP", consultaEm: antiga,
    situacao: "CRITICO", totalOcorrencias: 4, dividaTotal: 90000,
    restricoes: [{ tipo: "PROTESTO", quantidade: 3, valor: 60000 }, { tipo: "ACAO_JUDICIAL", quantidade: 1, valor: 30000 }],
    vinculos: [{ cnpj: "12345678000190", razaoSocial: "TECELAGEM PRIMAVERA LTDA" }],
  },
  {
    cpf: "44444444444", nome: "DIEGO CRITICO COSTA", cidade: "SUMARE", uf: "SP", consultaEm: hoje,
    situacao: "CRITICO", totalOcorrencias: 1, dividaTotal: 70000,
    restricoes: [{ tipo: "REFIN", quantidade: 1, valor: 70000 }], vinculos: [],
  },
];

async function entrar(page: Page) {
  await page.goto("/login");
  await page.fill('input[type="email"]', EMAIL);
  await page.fill('input[type="password"]', SENHA);
  await page.keyboard.press("Enter");
  await page.waitForURL(url => !url.pathname.startsWith("/login"));
}

/** Linhas da lista (os botões "Abrir …" fora do menu). */
const linhas = (page: Page) => page.locator("main button[aria-label^='Abrir ']:not([aria-label='Abrir menu'])");

test.describe("Pessoas Físicas", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/person/profiles/resumo", rota => rota.fulfill({ json: PESSOAS }));
    await entrar(page);
  });

  test("filtra por situação, ordena e guarda tudo na URL", async ({ page }) => {
    await page.goto("/individuals");
    await expect(linhas(page)).toHaveCount(4);

    await page.getByRole("button", { name: /Críticos/ }).click();
    await expect(linhas(page)).toHaveCount(2);

    await page.getByLabel("Ordenar").selectOption("divida");
    await expect(linhas(page).first()).toHaveAccessibleName("Abrir Carla Critica Lima");
    await expect(page).toHaveURL(/situacao=critico/);
    await expect(page).toHaveURL(/ordem=divida/);

    // Recarregar volta no mesmo estado — é o que acontece ao voltar da análise completa.
    await page.reload();
    await expect(linhas(page)).toHaveCount(2);
    await expect(page.getByRole("button", { name: /Críticos/ })).toHaveAttribute("aria-pressed", "true");
  });

  test("busca por nome sem acento e por pedaço do CPF", async ({ page }) => {
    await page.goto("/individuals");
    const busca = page.getByPlaceholder("Buscar por nome ou CPF");

    await busca.fill("bruno");
    await expect(linhas(page)).toHaveCount(1);

    await busca.fill("3333");
    await expect(linhas(page)).toHaveCount(1);
    await expect(linhas(page).first()).toHaveAccessibleName("Abrir Carla Critica Lima");

    await busca.fill("ninguem");
    await expect(page.getByText("Ninguém com esse nome ou CPF")).toBeVisible();
    await page.getByRole("button", { name: "Limpar filtros" }).click();
    await expect(linhas(page)).toHaveCount(4);
  });

  test("gaveta mostra restrições e vínculos e fecha com Esc", async ({ page }) => {
    await page.goto("/individuals?q=carla");
    await linhas(page).first().click();

    const gaveta = page.getByRole("dialog", { name: "Resumo de Carla Critica Lima" });
    await expect(gaveta).toBeVisible();
    await expect(gaveta.getByText("Protestos")).toBeVisible();
    await expect(gaveta.getByText("Tecelagem Primavera Ltda")).toBeVisible();
    await expect(gaveta.getByText(/Para uma decisão de crédito vale reconsultar/)).toBeVisible();
    await expect(gaveta.getByRole("link", { name: "Abrir análise completa" })).toHaveAttribute("href", "/individuals/33333333333");

    await page.keyboard.press("Escape");
    await expect(gaveta).toBeHidden();
  });
});
