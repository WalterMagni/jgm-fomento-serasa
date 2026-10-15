import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Esteira de prospecção unificada — caminho completo, de Nova até Habilitado.
 *
 * Roda contra o backend local (porta 8080) com o frontend que o playwright.config sobe na 3001.
 *
 * Variáveis:
 *   E2E_EMAIL / E2E_SENHA  usuário do teste (padrão: o admin do DatabaseSeeder)
 *   E2E_API                base da API (padrão: http://localhost:8080)
 *   E2E_COMITE=1           o usuário está marcado como Analista e Comitê: o teste segue pela
 *                          documentação, comitê, aceite e contrato até Habilitado. Sem isso, para
 *                          no Aprovado.
 *
 * O card de teste usa um CNPJ aleatório e é apagado no fim, passe ou falhe.
 */

const EMAIL = process.env.E2E_EMAIL ?? "admin@jgm.com.br";
const SENHA = process.env.E2E_SENHA ?? "admin123";
const API = process.env.E2E_API ?? "http://localhost:8080";
const COMITE = process.env.E2E_COMITE === "1";

const cnpj = Array.from({ length: 14 }, (_, i) => (i === 0 ? 9 : Math.floor(Math.random() * 10))).join("");
const razaoSocial = `E2E ESTEIRA ${cnpj.slice(-6)} LTDA`;

let token = "";
let cardId = "";

async function login(request: APIRequestContext) {
  const resposta = await request.post(`${API}/api/auth/login`, { data: { email: EMAIL, password: SENHA } });
  expect(resposta.ok(), "login na API").toBeTruthy();
  token = (await resposta.json()).token;
}

function auth() {
  return { Authorization: `Bearer ${token}` };
}

async function entrar(page: Page) {
  await page.goto("/login");
  await page.fill('input[type="email"]', EMAIL);
  await page.fill('input[type="password"]', SENHA);
  await page.keyboard.press("Enter");
  await page.waitForURL(url => !url.pathname.startsWith("/login"));
}

async function abrirCard(page: Page) {
  await page.goto(`/prospeccao?card=${cardId}`);
  await expect(page.getByRole("dialog", { name: `Card de ${razaoSocial}` })).toBeVisible();
}

test.describe.serial("Esteira de prospecção unificada", () => {
  test.beforeAll(async ({ request }) => {
    await login(request);
    const criado = await request.post(`${API}/api/v1/prospeccao`, {
      headers: auth(),
      data: { cnpj, razaoSocial, comercialNome: "Comercial E2E" },
    });
    expect(criado.status(), "abrir prospecção").toBe(201);
    cardId = (await criado.json()).id;
  });

  test.afterAll(async ({ request }) => {
    if (cardId) await request.delete(`${API}/api/v1/prospeccao/${cardId}`, { headers: auth() });
  });

  test("assumir e aprovar na análise inicial, sem limite: o card vai para Aprovado, com o comercial", async ({ page }) => {
    await entrar(page);
    await abrirCard(page);

    await page.getByRole("button", { name: "Assumir análise" }).click();
    await expect(page.getByRole("button", { name: "Decidir", exact: true })).toBeEnabled();

    await page.getByRole("button", { name: "Decidir", exact: true }).click();
    // Limite e produtos são do comitê: a análise inicial não pede.
    await expect(page.getByLabel("Limite", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Aprovar e avisar o comercial" }).click();
    await expect(page.getByText("Aprovado na análise inicial · limite definido no comitê")).toBeVisible();
    await expect(page.getByText(/Com o comercial: faltam \d+ obrigatório/)).toBeVisible();
  });

  test("pendência para o prazo e comentário entra na atividade", async ({ page }) => {
    await entrar(page);
    await abrirCard(page);

    await page.getByRole("button", { name: "Abrir pendência" }).click();
    await page.getByLabel("Pergunta da pendência").fill("Mandar faturamento dos últimos 12 meses");
    await page.getByLabel("Faturamento mensal").check();
    await page.getByRole("button", { name: "Abrir pendência", exact: true }).last().click();
    await expect(page.getByText("Aguardando comercial · prazo parado")).toBeVisible();

    await page.getByRole("button", { name: "Responder" }).click();
    await page.getByLabel("Resposta da pendência").fill("Faturamento chegou por e-mail");
    await page.getByRole("button", { name: "Registrar resposta" }).click();
    await expect(page.getByText("Aguardando comercial · prazo parado")).toBeHidden();

    await page.getByLabel("Novo comentário").first().click();
    await page.keyboard.type("Comentário do teste de ponta a ponta");
    await page.getByRole("button", { name: "Comentar" }).first().click();
    await expect(page.getByText("Comentário do teste de ponta a ponta").first()).toBeVisible();
  });

  test("documentos completos, comitê, aceite do cliente, contrato e habilitado", async ({ page, request }) => {
    test.skip(!COMITE, "Defina E2E_COMITE=1 com um usuário marcado como Analista e Comitê");

    // Resolve o checklist pela API: anexar doze arquivos não é o que este teste verifica. Com o
    // último obrigatório resolvido, o card sai sozinho do Aprovado para a Documentação.
    const detalhe = await (await request.get(`${API}/api/v1/prospeccao/${cardId}`, { headers: auth() })).json();
    for (const documento of detalhe.documentos) {
      await request.patch(`${API}/api/v1/prospeccao/documentos/${documento.id}`, {
        headers: auth(),
        data: { status: "NAO_APLICAVEL", motivo: "Teste de ponta a ponta" },
      });
    }

    await entrar(page);
    await abrirCard(page);

    await page.getByRole("button", { name: "Enviar ao comitê" }).first().click();
    await page.getByRole("button", { name: /^Comitê/ }).click();
    await page.getByRole("radio", { name: "Favorável", exact: true }).click();
    await page.getByRole("button", { name: "Registrar parecer" }).click();

    await page.getByRole("button", { name: "Registrar resultado" }).first().click();
    await page.getByRole("radio", { name: /Aprovar/ }).click();
    // Aprovar no comitê pede a proposta: limite e condições por produto.
    await page.getByLabel("Limite aprovado pelo comitê").fill("500000");
    await page.getByLabel("Produto da condição 1").selectOption("DUPLICATA");
    await page.getByLabel("Valor da condição 1").fill("500000");
    await page.getByRole("button", { name: "Registrar resultado", exact: true }).last().click();
    await expect(page.getByText("Limite aprovado: R$ 500.000,00")).toBeVisible();

    await page.getByRole("button", { name: "Registrar resposta do cliente" }).first().click();
    await page.getByRole("radio", { name: /Aceitou/ }).click();
    await page.getByRole("button", { name: "Registrar resposta", exact: true }).click();

    await page.getByRole("button", { name: "Concluir contrato" }).click();
    await page.getByLabel("Nome", { exact: true }).fill("Contato E2E");
    await page.getByLabel("Telefone").fill("(11) 90000-0000");
    await page.getByLabel("E-mail").fill("contato.e2e@exemplo.com.br");
    await page.getByRole("button", { name: "Salvar contato" }).click();
    await page.getByRole("button", { name: "Concluir: habilitado" }).click();
    await expect(page.getByText("Cedente habilitado. As operações seguem na esteira de liberação.")).toBeVisible();
  });
});
