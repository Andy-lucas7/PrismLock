# Documentação Completa do PrismLock

Guia prático e referência completa de interface, ferramentas, motor gráfico, variáveis globais e linguagem de fórmulas (**KWGT / KLWP**) do **PrismLock**.

---

## Sumário

1. [Visão Geral e Conceitos](#1-visão-geral-e-conceitos)
2. [Navegação Principal](#2-navegação-principal)
   - [2.1 Hero Page (Apresentação)](#21-hero-page-apresentação)
   - [2.2 Carrossel Principal (Theme Hub)](#22-carrossel-principal-theme-hub)
3. [O Estúdio Interativo (`StudioScreen`)](#3-o-estúdio-interativo-studioscreen)
   - [3.1 Gestos Diretos no Canvas 1:1](#31-gestos-diretos-no-canvas-11)
   - [3.2 Barra Superior do Estúdio](#32-barra-superior-do-estúdio)
4. [Barra de Contexto do Projeto (3 Pílulas Fixas)](#4-barra-de-contexto-do-projeto-3-pílulas-fixas)
   - [4.1 Pílula 1 — `Fundo & 3D` (ou `Formato & Fundo` em Widgets)](#41-pílula-1--fundo--3d-ou-formato--fundo-em-widgets)
   - [4.2 Pílula 2 — `Globais ($gv)`](#42-pílula-2--globais-gv)
   - [4.3 Pílula 3 — `Clima & Automação`](#43-pílula-3--clima--automação)
5. [As 5 Abas da Camada Selecionada](#5-as-5-abas-da-camada-selecionada)
   - [5.1 Aba 1 — `Camadas`](#51-aba-1--camadas)
   - [5.2 Aba 2 — `Conteúdo`](#52-aba-2--conteúdo)
   - [5.3 Aba 3 — `Forma & Fonte`](#53-aba-3--forma--fonte)
   - [5.4 Aba 4 — `Vidro & Cor`](#54-aba-4--vidro--cor)
   - [5.5 Aba 5 — `Posição & Toque`](#55-aba-5--posição--toque)
6. [Botão Calculadora (`ƒx`) nos Controles](#6-botão-calculadora-fx-nos-controles)
7. [Referência da Linguagem de Fórmulas do PrismLock](#7-referência-da-linguagem-de-fórmulas-do-prismlock)
   - [7.1 Sintaxe Básica (`$...$` vs `{...}`)](#71-sintaxe-básica--vs-)
   - [7.2 Operadores Matemáticos, Comparadores e Lógicos](#72-operadores-matemáticos-comparadores-e-lógicos)
   - [7.3 Data e Hora — `df()`](#73-data-e-hora--df)
   - [7.4 Contagem de Tempo — `tf()`](#74-contagem-de-tempo--tf)
   - [7.5 Bateria — `bi()`](#75-bateria--bi)
   - [7.6 Sistema e Áudio — `si()`](#76-sistema-e-áudio--si)
   - [7.7 Clima (Open-Meteo) — `wi()`](#77-clima-open-meteo--wi)
   - [7.8 Astronomia e Dia/Noite — `ai()`](#78-astronomia-e-dianoite--ai)
   - [7.9 Mídia — `mi()`](#79-mídia--mi)
   - [7.10 Variáveis Globais — `gv()`](#710-variáveis-globais--gv)
   - [7.11 Condicionais — `if()`](#711-condicionais--if)
   - [7.12 Manipulação de Cores — `ce()`](#712-manipulação-de-cores--ce)
   - [7.13 Laços / Repetições — `fl()`](#713-laços--repetições--fl)
   - [7.14 Texto e Formatação — `tc()`](#714-texto-e-formatação--tc)
   - [7.15 Funções Matemáticas — `mu()`](#715-funções-matemáticas--mu)
   - [7.16 Tags Rápidas Legadas e Expressões `{=...}`](#716-tags-rápidas-legadas-e-expressões-)
   - [7.17 Lista Completa dos 58 Ícones Vetoriais Nativos](#717-lista-completa-dos-58-ícones-vetoriais-nativos)
8. [Exportação e Importação Portátil (`.prism` e `.widget.prism`)](#8-exportação-e-importação-portátil-prism-e-widgetprism)
9. [Tutoriais Passo a Passo (Receitas Práticas)](#9-tutoriais-passo-a-passo-receitas-práticas)
10. [Modelos Prontos por Estilo](#10-modelos-prontos-por-estilo)
11. [Desempenho, Memória e Bateria](#11-desempenho-memória-e-bateria)

---

## 1. Visão Geral e Conceitos

O **PrismLock** reúne em um único aplicativo dois modos de personalização gráfica em **120Hz**:

- **Telas de Bloqueio & Live Wallpaper (estilo KLWP):** cria telas animadas em tela cheia com recorte 3D automático de sujeito, vídeos em segundo plano, ações de toque na Tela Inicial e cristal líquido refratando em tempo real.
- **Widgets Independentes (estilo KWGT):** cria widgets em proporções dedicadas (`2×2`, `4×2`, `4×1.8`, `4×1`) que podem ser fixados diretamente na Tela Inicial ou no LockStar, utilizando uma foto de referência própria do widget para calcular refração e desfoque reais sem consumo em repouso. Sem foto própria, o vidro refrata um fundo neutro grafite (o wallpaper do launcher não pode ser lido, e a foto da tela de bloqueio não é o que fica atrás do widget).

O serviço de papel de parede e o provedor de widgets rodam isolados no processo `:wallpaper`. Com o aparelho bloqueado, só o quadro final pronto (`RGB_565`, ~5 MB) fica em memória para despertar instantâneo; todo o resto é liberado (ver [Seção 11](#11-desempenho-memória-e-bateria)).

---

## 2. Navegação Principal

### 2.1 Hero Page (Apresentação)

Exibida na primeira abertura do aplicativo (ou sempre que tocar no botão `✦` no topo do Carrossel):

- Apresenta a coreografia prismática e os destaques do motor (*Liquid Crystal · Elastic Engine · Optical 3D Depth*).
- Toque no botão branco central para abrir o portal radial de entrada rumo ao Carrossel Principal.
- Durante a apresentação, toques e arrastos fora do botão são absorvidos pela própria Hero Page — não chegam ao card do Carrossel por baixo (que abriria o Estúdio).

### 2.2 Carrossel Principal (Theme Hub)

É a tela central onde ficam organizados suas **Telas** e seus **Widgets** salvos.

| Área / Botão | Ícone / Visual | O que faz |
| :--- | :--- | :--- |
| **Botão Rever Apresentação** | `✦` (círculo translúcido no topo direito) | Reexibe a Hero Page interativa. |
| **Botão Importar** | Seta para baixo (`↓`) | Abre menu com duas opções:<br>• **Importar Arquivo (`.prism` / `.zip`)**: carrega um tema (`.prism`), widget (`.widget.prism`) ou `.zip`, detectando automaticamente se é Tela ou Widget.<br>• **Importar Pasta**: importa uma pasta descompactada contendo `theme.json` ou `widget.json`. |
| **Botão Criar Novo** | `+` (círculo branco sólido) | Cria uma nova **Tela de Bloqueio** ou um **Novo Widget** (conforme o modo ativo na pílula central) solicitando o nome inicial e abrindo direto no Estúdio. |
| **Seletor Central (Pílula Líquida)** | `Telas (Lock)` \| `Widgets (KWGT)` | Alterna com transição suave (*fade + blur*) entre o carrossel de Wallpapers e o carrossel de Widgets. |
| **Gestos no Card Central** | Deslizar horizontal / vertical | • **Deslizar Esq./Dir.**: alterna entre projetos salvos.<br>• **Arrastar para Cima (`↑`)**: abre o projeto selecionado no Estúdio.<br>• **Arrastar para Baixo (`↓`)**: aplica na Tela de Bloqueio ou fixa o Widget na Tela Inicial. |
| **Dock — Botão Editar** | Lápis (`✎`) à esquerda | Abre o item selecionado no **Estúdio Interativo**. |
| **Dock — Botão Aplicar / Fixar** | Check (`✓`) ou Pino (`📌`) branco ao centro | • Em **Telas**: assa e aplica o papel de parede vivo no sistema.<br>• Em **Widgets**: salva o layout e solicita a fixação (`requestPin`) na Tela Inicial. |
| **Dock — Botão Excluir** | Lixeira à direita | Abre diálogo de confirmação para remover o tema ou widget salvo. |

> **Dica de primeiro uso:** No Carrossel em modo **Telas (Lock)**, caso o sistema peça para desativar a otimização de bateria do PrismLock, aceite no banner exibido para evitar encerramento do papel de parede vivo em segundo plano.

---

## 3. O Estúdio Interativo (`StudioScreen`)

Ao editar uma Tela ou Widget, você entra no **Estúdio 1:1**, dividido entre o **Canvas ao vivo** (acima) e o **Painel Inspetor em Vidro Fosco** (abaixo).

### 3.1 Gestos Diretos no Canvas 1:1

- **Toque em qualquer item visível:** seleciona a camada correspondente (no modo Widget, o preview exibe a proporção exata escolhida para o widget).
- **Arrastar com 1 dedo:** move a camada pela tela. Ao passar próximo ao centro horizontal (`50%`) ou vertical, guias magnéticas (*snap*) ajudam a centralizar perfeitamente.
- **Pinça com 2 dedos:** ajusta **Escala** e **Rotação** da camada selecionada em tempo real.
- **Durante o arraste:** a barra superior e o painel inferior ficam temporariamente translúcidos/ocultos para você enxergar 100% da composição.

### 3.2 Barra Superior do Estúdio

| Controle | Visual | Função |
| :--- | :--- | :--- |
| **Voltar ao Carrossel** | `←` (círculo esquerdo) | Salva as alterações, limpa caches de preview e retorna ao Hub mantendo o item focado. |
| **Pílula do Título** | Texto com nome + ícone `✎` | Toque para renomear a Tela ou o Widget sem sair do Estúdio. |
| **Cadeado da Camada** | `🔒` / `🔓` | Aparece quando há uma camada selecionada. Quando **travada (branco)**, a camada não pode ser movida ou selecionada acidentalmente pelo toque no canvas. |
| **Recolher / Abrir Painel** | `⌄` / ícone de sliders | Oculta ou reexibe o painel inspetor inferior para pré-visualizar o layout inteiro sem obstruções. |
| **Menu 3 Pontos** | `⋮` | Abre ações do projeto:<br>• **Renomear Tela**<br>• **Salvar** (grava imediatamente em disco)<br>• **Salvar como Novo Tema** (duplica o projeto atual com novo nome)<br>• **Aplicar na Tela de Bloqueio / Aplicar & Fixar Widget**<br>• **Exportar Pacote (`.prism` / `.widget.prism`)** |

---

## 4. Barra de Contexto do Projeto (3 Pílulas Fixas)

No topo do painel inferior ficam sempre acessíveis **3 botões rápidos do projeto inteiro**, separados das propriedades da camada selecionada. Tocar em uma pílula abre o painel correspondente (tocar novamente ou clicar em **`Voltar às Abas`** fecha e retorna às abas da camada).

### 4.1 Pílula 1 — `Fundo & 3D` (ou `Formato & Fundo` em Widgets)

#### No modo Tela de Bloqueio (`isWidget = false`)
- **Pasta Carrossel SAF:** selecione uma pasta do aparelho cheia de fotos/vídeos para o PrismLock alternar automaticamente.
- **Foto / Vídeo:** selecione uma única imagem ou arquivo de vídeo (`.mp4`, `.webm`, `.mkv`, `.mov`) como papel de parede.
- **Lixeira (Limpar fundo):** aparece quando há pasta ou mídia ativa para remover o fundo atual.
- **Galeria de Miniaturas:**
  - Toque na miniatura para testar instantaneamente como o tema fica com aquela imagem/vídeo.
  - No canto superior direito de cada foto há o selo **`3D ON` / `3D OFF`** (vídeos exibem ícone `▶`, pois recorte 3D é exclusivo para fotos).
- **Seção `Profundidade 3D (Bokeh & Color Pop)`** *(exibida automaticamente quando a foto ativa está com `3D ON`)*:
  - **Proteção Cobertura Relógio:** evita que um sujeito muito alto cubra mais de 35% do relógio principal.
  - **Presets rápidos:** `Original`, `Modo Retrato`, `Color Pop P&B`, `Cine 3D`.
  - **Bokeh Fundo (`0%–100%`):** desfoca progressivamente apenas o plano de fundo atrás do sujeito.
  - **Fundo P&B (`0%–100%`):** retira a cor do plano de fundo mantendo o sujeito colorido.
  - **Inverter Color Pop (Sujeito P&B):** inverte o efeito deixando o sujeito em preto e branco e o fundo colorido.
  - **Destaque Sujeito (`0%–100%`):** realça contraste/brilho do sujeito recortado.
  - **Vinheta & Halo 3D (`0%–100%`):** adiciona profundidade cinematográfica separando sujeito e fundo.
- **Aplicar na Tela de Bloqueio:** botão de ação rápida ao final do painel.

#### No modo Widget (`isWidget = true`)
- **Formato do Widget (KWGT / Home):** escolha a proporção ideal do widget:
  - `Quadrado 2×2` (`1.05:1`)
  - `Card 4×2` (`2.0:1`)
  - `Mídia 4×1.8` (`2.2:1`)
  - `Faixa 4×1` (`3.4:1`)
- **Wallpaper de Referência (Refração):** escolha uma foto para simular o papel de parede sob o widget; camadas em **Blur Clássico** ou **Cristal Óptico** usam essa referência como mapa de refração óptica.
- **Aplicar & Fixar Widget na Tela:** salva o widget, atualiza todas as instâncias ativas e solicita a fixação no launcher.

### 4.2 Pílula 2 — `Globais ($gv)`

Centraliza todas as variáveis globais do projeto (`Project.globals`). Alterar uma global modifica instantaneamente todas as camadas vinculadas a ela.

- **Atalhos de criação no topo:** `+ Nova Global` ou criação rápida por tipo:
  - **`+ Cor`**: armazena um código `#AARRGGBB` com seletor de cor ao vivo.
  - **`+ Interruptor`**: alterna entre `"0"` (desligado) e `"1"` (ligado).
  - **`+ Texto`**: texto livre reutilizável em fórmulas ou nomes de ativos.
  - **`+ Número`**: valor numérico com botões `-` e `+` rápidos e edição por toque.
  - **`+ Lista`**: conjunto de opções separadas por vírgula (ex.: `Clima,Bateria,Música`) selecionáveis por chip ou cicladas via toque.
- **Cartão de cada Global:**
  - Mostra o nome, o tipo e o código de fórmula (ex.: `$gv(cor_destaque)$`).
  - Controle interativo direto no cartão (Switch, círculo de Cor, `-`/`+` Numérico, pílula de Texto ou chips da Lista) e botão lixeira para excluir.
  - **Aplicar em:** chips com as camadas compatíveis (e chip **`Todas`** para globais de Cor) para vincular ou desvincular automaticamente a global na cor (`fxColor`), visibilidade (`visibilityExpr`), conteúdo/ativo (`fxAsset` / `text`) ou barra (`value`) da camada escolhida.

### 4.3 Pílula 3 — `Clima & Automação`

- **Cidade (Clima Open-Meteo):** toque para digitar o nome da sua cidade (ex.: `São Paulo`); o PrismLock consulta as coordenadas via Open-Meteo e atualiza temperatura (`wi(temp)`), condição (`wi(cond)`), código meteorológico (`wi(code)`) e dia/noite (`ai(isday)`).
- **Regras do Carrossel de Wallpapers** *(no modo Tela)*:
  - **Trocar ao apagar tela:** avança automaticamente para a próxima mídia da pasta SAF quando você desliga/acorda a tela.
  - **Trocar com toque duplo:** permite trocar de papel de parede dando dois toques rápidos na tela.
  - **Intervalo (`0m–120m`):** tempo mínimo em minutos para troca periódica (`0m` desativa troca por tempo).
- **Escurecer (Dim) (`0%–70%`):** aplica um véu escuro sobre o wallpaper de fundo para melhorar a leitura de relógios claros.

---

## 5. As 5 Abas da Camada Selecionada

Logo abaixo das 3 pílulas de contexto está a barra das **5 Abas de edição da camada ativa**.

### 5.1 Aba 1 — `Camadas`

Gerencia a árvore de elementos do tema ou widget.

- **Botão `+ Adicionar Item` (branco):** abre a faixa horizontal com todos os tipos de camada disponíveis:
  - `Texto/Código`: relógios, datas, métricas ou qualquer fórmula `$...$`.
  - `Pilha Vertical` / `Pilha Horizontal`: grupos (`STACK`) que empilham e alinham camadas filhas automaticamente com espaçamento configurável (se uma Pilha estiver selecionada ao adicionar um novo item, ele nasce como filho dela).
  - `Forma`: retângulo, círculo, linha.
  - `Progresso`: barra ou anel vinculado a bateria, dia, hora ou fórmula (`0..100`).
  - `Ícone`: ícone vetorial nativo (ou dinâmico de clima/ativos).
  - `Imagem / Ativo`: foto, PNG com transparência ou ativo dinâmico nomeado.
  - `SVG`: importa um arquivo vetorial `.svg` externo direto como camada escalável (suporta Vidro e Cristal Óptico).
  - `Profundidade 3D`: camada especial que renderiza o sujeito recortado naquela posição Z-Index (camadas acima dela ficam na frente do sujeito; camadas abaixo ficam atrás do sujeito).
- **Botão `Modelos Prontos`:** abre janela modal com layouts completos (inclusive modelos exclusivos no modo Widget), pedindo confirmação antes de substituir as camadas atuais.
- **Lista de Camadas:** cada cartão exibe ícone do tipo, nome, prévia do conteúdo (ou ação de toque configurada) e **6 botões rápidos à direita**:
  1. `↑` Move a camada uma posição para cima (atrás na ordem de desenho).
  2. `↓` Move a camada uma posição para baixo (mais à frente / Z-Index superior).
  3. `👁` Oculta ou exibe a camada sem apagá-la.
  4. `🔒` Trava ou destrava a manipulação por gestos no canvas.
  5. `❐` Duplica a camada (incluindo todas as filhas se for uma Pilha).
  6. `🗑` Exclui a camada.

### 5.2 Aba 2 — `Conteúdo`

Adapta-se automaticamente ao tipo da camada selecionada e começa sempre com **Nome da camada** (toque para editar).

- **Quando for `TEXT` (Texto/Código):**
  - **Fórmula de Texto / KWGT (`$...$`):** abre diálogo amplo para digitar texto misto com fórmulas e tags.
  - **Resultado ao vivo:** barra em tempo real mostrando exatamente o texto já avaliado.
  - **Assistente de Fórmulas (KWGT):** categorias (*Data/Hora*, *Bateria/Sistema*, *Clima*, *Lógica / KLWP*, *FX / Cores*, *Tags Legadas*), cartões clicáveis para inserir trechos, presets rápidos (`Substituir Relógio`, `Data Completa`, `Clima & Bat`, `Limpar`) e botões de inserção direta das **Globais do Projeto** criadas.
- **Quando for `ICON` (Ícone Vetorial):**
  - **Seletor Visual de Ícones (`Icons.names` — 59 opções):** grade horizontal dupla mostrando o desenho vetorial real + nome em português dos **58 ícones nativos** + modo **`Clima Auto`** (`weather`).
  - **Fórmula direta no Ícone (`ƒx`):** permite digitar ou abrir o editor `ƒx` direto no campo `icon` (ex.: `$if(bi(charging), "bolt", "battery")$`).
  - **Biblioteca Visual de Ativos Nomeados** *(ver abaixo)*.
- **Quando for `SVG`:**
  - Preview vetorial atual + botão **`Substituir arquivo SVG`** para importar outro `.svg`.
  - **Biblioteca Visual de Ativos Nomeados** *(ver abaixo)*.
- **Quando for `IMAGE` (Imagem / Ativo):**
  - Botão **`Escolher Foto / PNG / WebP para Camada`** para definir uma imagem direta na camada.
  - **Biblioteca Visual de Ativos Nomeados:**
    1. Toque em **`+ Adicionar Ativo`**, digite um apelido curto (ex.: `sol`, `chuva`, `noite`) e selecione qualquer **`.svg`**, **`.png`**, **`.webp`** ou **`.jpg`** no aparelho.
    2. Cada ativo salvo aparece em um cartão com sua **pré-visualização real desenhada** (miniatura da foto/PNG ou vetor SVG renderizado).
    3. Toque no **cartão** para aplicar o ativo à camada selecionada.
    4. Toque no **apelido (`✎`)** na base do cartão para renomear o apelido (atualizando referências das camadas automaticamente), ou no ícone `🗑` para removê-lo da biblioteca.
    5. Use o campo **`Ativo / Ícone Dinâmico (fxAsset)`** com o botão **`ƒx`** para trocar automaticamente entre ativos SVG, imagens PNG ou ícones nativos com base em fórmulas (funciona igualmente em camadas `ICON`, `SVG` e `IMAGE`).
- **Quando for `SHAPE` com `BAR` ou `RING` (Progresso):**
  - **Variável / Fórmula Progresso (`0..100`):** selecione ou digite a fonte do progresso (`Bateria`, `Dia %`, `Hora %`, `Passos` ou fórmulas como `$=hour/24*100$`).
  - **Espessura Anel** (no modo `RING`) e **Cor da Trilha Fundo** (trilha inativa atrás da barra/anel).
- **Quando for `STACK` (Pilha):**
  - Direção (`Vertical` / `Horizontal`) e presets/slider de **Espaçamento** (`-60px` a `160px`, permitindo inclusive sobreposição com valores negativos).

### 5.3 Aba 3 — `Forma & Fonte`

Adapta-se ao tipo da camada:

- **Para `TEXT`:**
  - **Fonte, Peso & Tamanho:**
    - `Ver Lista (N)`: abre o modal tipográfico completo exibindo o nome e a prévia `"12:45"` desenhada com cada fonte disponível (sistema Samsung detectado em runtime, 8 fontes OFL embarcadas + importadas).
    - `Pasta de Fontes`: vincula uma pasta SAF com `.ttf` / `.otf`.
    - `Atualizar (↻)`: ressincroniza novas fontes adicionadas à pasta.
    - `+ TTF/OTF`: importa um arquivo de fonte avulso.
    - Faixa horizontal de chips com preview visual de cada fonte.
    - Presets de peso (`Leve 300` até `Extra 800`), slider **Tamanho** *(com botão **`ƒx`** ligado a `fxSize`)* e slider **Peso** (`100–900`).
  - **Elasticidade, Kerning & Alinhamento (`StemStretch`):**
    - Presets rápidos: `Clássico 1.0x`, `Alto 1.4x`, `Editorial 1.8x`, `Ultra 2.4x`.
    - **Elasticidade (`1.0x–3.0x`):** estica verticalmente as hastes das letras preservando arcos e terminais sem estrangular os traços.
    - **Comp. Traço (`0.0x–1.2x`):** compensação trigonométrica automática da espessura dos traços horizontais quando esticados.
    - **Kerning (`-0.15em–0.60em`):** espaçamento horizontal entre letras.
    - **Entrelinha (`-0.60–1.00`):** distância vertical em textos de múltiplas linhas.
    - **Eixos Variáveis:** exibidos automaticamente se a fonte possuir eixos OpenType (`wght`, `wdth`, etc.).
    - **Alinhamento:** `Esquerda`, `Centro`, `Direita`.
- **Para `SHAPE`, `ICON`, `SVG` e `IMAGE`:**
  - **Geometria da Forma** *(para `SHAPE`)*: `Retângulo`, `Círculo`, `Linha`, `Barra`, `Anel`.
  - **Dimensões & Cantos:**
    - Presets rápidos: `Quadrado`, `Pílula`, `Card`, `Barra Fina`.
    - **Largura (`16px–1080px`)** *(com botão **`ƒx`** ligado a `fxSize` — escala largura e altura proporcionalmente via fórmula)*.
    - **Altura (`2px–1080px`)** e **Cantos (`0px–300px`)** para arredondamento de bordas.
- **Para `STACK`:**
  - Direção, presets de Espaçamento, slider Espaçamento e Alinhamento dos filhos dentro da pilha.

### 5.4 Aba 4 — `Vidro & Cor`

No topo da aba há chips para alternar rapidamente entre camadas pintáveis sem precisar voltar à aba Camadas.

- **Preenchimento & Vidro (6 modos):**
  1. **Sólido:** cor única definida no painel **Cor Principal** *(com botão **`ƒx`** ligado a `fxColor`)* ou por **Globais de Cor (`$gv$`)**.
  2. **Gradiente:** combina **Cor Principal** *(com **`ƒx`**)* e **Cor Secundária**, com tipo (`Linear`, `Radial`, `Circular`) e **Ângulo Grad** (`0°–360°`).
  3. **Adaptativo:** extrai automaticamente a cor harmônica predominante do papel de parede ativo *(aceita sobrescrita via **`ƒx`** em Cor Principal)*.
  4. **Blur Clássico:** vidro fosco com desfoque gaussiano 1:1 atrás da camada (inclusive em textos, SVGs, ícones e **camadas `IMAGE`** usando o canal Alpha da imagem). Controles:
     - Presets: `Blur Suave`, `Fosco Intenso`, `Vidro Fumê`.
     - **Blur Gauss**, **Saturação**, **Granulação**, **Borda Suave** e **Tint Translúcido** *(com **`ƒx`**)*.
  5. **Cristal Óptico:** shader AGSL físico com refração real da imagem/vídeo de fundo através da silhueta da camada (funciona inclusive com preenchimento 0% opaco e em **`LayerType.IMAGE` com canal Alpha como mapa de lente 3D**). Controles:
     - Presets: `Cristal Limpo`, `Vidro Fosco`, `Prisma Forte`, `Zero Tint`.
     - **Refração (`0%–100%`)**, **Chanfro (`1–48px`)**, **Dispersão RGB (`0–20‰`)**, **Brilho Fresnel (`0.00–1.00`)**, **Ângulo Luz (`0°–360°`)**, **Jateamento (`0.00–1.00`)**, **Blur Jato** *(aparece quando Jateamento > 0)* e **Tint Cristal** *(com **`ƒx`**)*.
  6. **Vazado:** remove o preenchimento interno e desenha apenas o contorno da camada (suporta gradiente no contorno).
- **Globais de Cor (`$gv$`):** pílulas clicáveis para vincular uma global de cor ao campo `fxColor` com 1 toque.
- **Contorno / Borda:** presets (`Sem Borda`, `Fino 2px`, `Médio 6px`, `Forte 12px`), **Espessura (`0–24px`)** e **Cor Contorno**.
- **Sombra Projetada:** presets (`Sem Sombra`, `Suave`, `Dramática`), **Raio Sombra (`0–50px`)**, **Sombra X**, **Sombra Y** e **Cor Sombra**.
- **Opacidade & Mesclagem:** presets (`100%`, `85%`, `60%`, `35%`), slider **Opacidade** *(com botão **`ƒx`** ligado a `fxOpacity`)* e **17 Modos de Mesclagem (`Blend`)** (*Normal, Multiplicar, Tela, Sobrepor, Escurecer, Clarear, Subexposição/Superexposição de Cor, Luz Suave, Luz Dura, Diferença, Exclusão, Adicionar, Matiz, Saturação, Cor, Luminosidade*).

### 5.5 Aba 5 — `Posição & Toque`

- **Posição, Âncora & Transformação:**
  - Se a camada for **filha de uma Pilha (`STACK`)**, é exibido um aviso informando que a posição base segue a pilha pai, com botão **`Selecionar Pilha Pai`** e botões rápidos **`ƒx Desloc. X`** e **`ƒx Desloc. Y`**.
  - Para camadas independentes: presets (`Topo Centro`, `Centro`, `Rodapé`), sliders **Posição X** e **Posição Y** com botões de ajuste milimétrico **`-1px`** / **`+1px`** e botões **`ƒx`** (conectados a `fxOffsetX` / `fxOffsetY`), além dos 9 pontos de **Âncora** (*Topo Esquerda*, *Topo Centro*, *Topo Direita*, *Centro Esquerda*, *Centro*, *Centro Direita*, *Base Esquerda*, *Base Centro*, *Base Direita*).
  - **Rotação (`-180°` a `180°`)** com presets (`0°`, `-90°`, `90°`) e botão **`ƒx`** (`fxRotation`).
  - **Escala (`0.25x` a `4.00x`)** com botão `Resetar Escala` e botão **`ƒx`** (`fxScale`).
- **Visibilidade da Camada:**
  - Chips de regra rápida: `Sempre`, `Apenas Bloqueio`, `Apenas Início`, `Carregando`, `Na Bateria`, `Bateria Baixa`, `Durante o Dia`, `À Noite`.
  - **Visibilidade por Expressão (`visibilityExpr`):** linha dedicada com botão **`ƒx`** para exibir/ocultar a camada por qualquer fórmula booleana.
- **Ação ao Clicar (Toque):**
  - **Testar Toques na Tela:** quando ativado, tocar nos itens do preview executa a ação configurada ali mesmo para testes imediatos.
  - **Preview: Bloqueio / Início:** alterna a simulação entre Tela de Bloqueio e Tela Inicial (útil para testar camadas `Apenas Início` ou `Apenas Bloqueio`).
  - **Tipos de Ação:**
    - `Nenhum`: camada não intercepta cliques.
    - `Abrir Aplicativo`: botão **`Escolher Aplicativo`** (abre lista pesquisável dos apps instalados) ou entrada manual de **`Pacote`** (`com.whatsapp`, etc.).
    - `Abrir Link / URI`: aceita URL, `tel:`, `mailto:`, deep link (`spotify:`) e fórmulas (ex.: busca no Google por clima da cidade), com presets `Google`, `YouTube`, `Spotify`, `Clima Busca`.
    - `Alternar Global`: alterna interruptores `0 ↔ 1`, avança listas ou soma `+1` em números (com chips de globais existentes e atalho **`Globais →`** para abrir a pílula de Globais).
    - `Definir Global`: grava um valor ou resultado de fórmula `$...$` na global alvo.
    - `Ciclar Global / Contador`: avança para a próxima opção de uma Lista ou soma `+1` em um Número.
    - `Próximo Wallpaper`: avança o carrossel SAF na Tela Inicial.
    - `Mídia: Play / Pause`, `Mídia: Próxima`, `Mídia: Anterior`: envia eventos de mídia ao player ativo.
    - `Abrir Alarme / Relógio`, `Abrir Câmera`, `Abrir Configurações`, `Abrir PrismLock`: atalhos rápidos de sistema.
  - **Executar Ação Agora:** botão para disparar imediatamente a ação configurada na camada.

---

## 6. Botão Calculadora (`ƒx`) nos Controles

O botão **`ƒx`** aparece diretamente ao lado dos controles principais do Estúdio:

| Controle | Aba | Propriedade Dinâmica | O que a fórmula deve retornar |
| :--- | :--- | :--- | :--- |
| **Ativo / Ícone Dinâmico** | Conteúdo | `fxAsset` | Apelido de ativo salvo (`"chuva"`, `"sol"`) ou nome de ícone nativo (`"sun"`, `"bolt"`). |
| **Tamanho / Largura** | Forma & Fonte | `fxSize` | Número em `px` (`8..600` para texto; `8..2000` para largura com altura proporcional). |
| **Cor Principal / Tint** | Vidro & Cor | `fxColor` | Código hexadecimal `#RRGGBB` ou `#AARRGGBB` (ou via `ce()` / `$gv(cor)$`). |
| **Opacidade** | Vidro & Cor | `fxOpacity` | Percentual `0..100` multiplicado pela opacidade base da camada. |
| **Posição X / Posição Y** | Posição & Toque | `fxOffsetX` / `fxOffsetY` | Deslocamento relativo em `px` (`-` esquerda/cima, `+` direita/baixo). |
| **Rotação** | Posição & Toque | `fxRotation` | Ângulo adicional em graus somado à rotação base. |
| **Escala** | Posição & Toque | `fxScale` | Fator multiplicador (`0.05..8.0`). |
| **Visibilidade** | Posição & Toque | `visibilityExpr` | Expressão verdadeira/falsa (`1`/`true` exibe; `0`/`false`/vazio oculta). |

### Como funciona o Editor `ƒx` (`FxExpressionDialog`)

1. Toque no botão **`ƒx`** ao lado do controle desejado.
2. O diálogo abre na metade superior da tela (sem cobrir o teclado) exibindo:
   - **Campo de fórmula:** digite ou edite a expressão.
   - **Resultado ao vivo:** prévia instantânea no canto superior direito mostrando o valor já calculado.
   - **Barra de atalhos rápidos:** pílulas clicáveis para inserir **`gv()`**, **`wi()`**, **`ai()`**, **`df()`**, **`bi()`**, **`if()`** e **`ce()`** na posição atual do cursor.
   - **Chips das suas Globais:** pílulas prontas `$gv(nome)$` para cada variável global criada no projeto.
3. Ao confirmar, o botão **`ƒx`** fica **Branco Puro (`#FFFFFF`)** e um selo `ƒx: ...` aparece abaixo do slider indicando que o controle está automatizado por expressão.
4. Para voltar ao controle manual pelo slider/cor, abra **`ƒx`** e toque em **`Limpar ƒx`**.

---

## 7. Referência da Linguagem de Fórmulas do PrismLock

### 7.1 Sintaxe Básica (`$...$` vs `{...}`)

- **Blocos `$ ... $` (Motor Kustom completo):** qualquer expressão entre cifrões é avaliada e substituída no resultado. Pode haver múltiplos blocos misturados com texto normal:
  - Exemplo: `"Hoje é $df(EEEE)$, bateria em $bi(level)$%"` → `"Hoje é quarta-feira, bateria em 84%"`
- **Aspas em argumentos de texto:** textos literais dentro de funções usam aspas duplas `"..."` ou simples `'...'` (ex.: `$if(bi(charging), "Carregando", "Bateria")$`).

### 7.2 Operadores Matemáticos, Comparadores e Lógicos

| Tipo | Operadores | Observação |
| :--- | :--- | :--- |
| **Aritméticos** | `+`, `-`, `*`, `/`, `%` (resto) | `+` também concatena textos quando pelo menos um lado não é número. |
| **Comparação** | `=`, `==`, `!=`, `<`, `>`, `<=`, `>=` | Comparação numérica quando ambos são números; caso contrário alfabética. |
| **Lógicos** | `&`, `&&` (E), `\|`, `\|\|` (OU), `!` (NÃO) | Verdadeiro é `1` (ou qualquer número ≠ 0 / texto não vazio diferente de `"0"` e `"false"`). |
| **Parênteses** | `( ... )` | Define precedência livremente. |

### 7.3 Data e Hora — `df()`

Usa padrões `DateTimeFormatter` (Java/Android) além de atalhos Kustom:

| Chamada | Retorno / Exemplo |
| :--- | :--- |
| `$df(HH)$` / `$df(hh)$` | Hora 24h (`00–23`) / Hora 12h (`01–12`). |
| `$df(mm)$` / `$df(ss)$` | Minuto (`00–59`) / Segundo (`00–59` — atualiza a cada 1s automaticamente). |
| `$df(HH:mm)$` | Hora e minuto formatados (`18:45`). |
| `$df(EEEE)$` / `$df(EEE)$` | Dia da semana completo (`terça-feira`) / abreviado (`ter.`). |
| `$df(d)$` / `$df(dd)$` | Dia do mês (`5` / `05`). |
| `$df(MMMM)$` / `$df(MMM)$` / `$df(MM)$` | Mês por extenso (`novembro`), abreviado (`nov.`) ou numérico (`11`). |
| `$df(yyyy)$` / `$df(dd/MM/yyyy)$` | Ano (`2026`) / data completa (`18/11/2026`). |
| `$df(D)$` | Dia do ano (`1..366`). |
| `$df(S)$` / `$df(f)$` | Epoch em segundos. |

### 7.4 Contagem de Tempo — `tf()`

Calcula a diferença até um horário ou data alvo:

- **Sintaxe:** `$tf(alvo)$` ou `$tf(alvo, modo)$`
- **Alvos aceitos:** `"HH:mm"`, `"HH:mm:ss"`, `"YYYY-MM-DD"`, ISO `LocalDateTime` ou vindo de outra função como `$tf(si(alarm))$`.
- **Modos opcionais:** `"D"` (dias), `"H"` (horas), `"M"` (minutos), `"S"` (segundos), ou omitido para formato legível (`2h 15m`).

### 7.5 Bateria — `bi()`

| Função | Tipo | Retorno / Exemplo |
| :--- | :--- | :--- |
| `$bi(level)$` | Número | Percentual atual (`0..100`). |
| `$bi(charging)$` | Booleano | `1` se conectado ao carregador, `0` caso contrário. |
| `$bi(status)$` | Texto | `"Carregando"` ou `"Descarregando"`. |
| `$bi(temp)$` | Número | Temperatura da bateria em °C (ex.: `34.2`). |

### 7.6 Sistema e Áudio — `si()`

| Função | Retorno / Exemplo |
| :--- | :--- |
| `$si(alarm)$` | Horário do próximo alarme do sistema (`"07:00"` ou `"--:--"`). |
| `$si(volm)$` | Volume atual de mídia em `%` (`0..100`). |
| `$si(mplaying)$` | `1` se áudio/música estiver tocando, `0` se pausado. |
| `$si(ringer)$` | `"Normal"`, `"Vibrar"` ou `"Silencioso"`. |
| `$si(locked)$` | `1` quando na Tela de Bloqueio, `0` na Tela Inicial. |
| `$si(steps)$` | Contagem de passos disponível no ambiente. |
| `$si(model)$` / `$si(man)$` / `$si(brand)$` | Modelo, fabricante e marca do aparelho. |
| `$si(sdk)$` | Nível da API Android (ex.: `35`). |

### 7.7 Clima (Open-Meteo) — `wi()`

Configurado pela cidade na pílula **Clima & Automação**:

| Função | Retorno / Exemplo |
| :--- | :--- |
| `$wi(temp)$` | Temperatura atual (ou `"--"` antes da primeira coleta). |
| `$wi(cond)$` | Descrição em português (`Céu limpo`, `Parcialmente nublado`, `Nublado`, `Neblina`, `Garoa`, `Chuva`, `Neve`, `Tempestade`). |
| `$wi(code)$` | Código meteorológico WMO (`0` = limpo, `1..2` = parcial, `3` = nublado, `45..48` = neblina, `51..57` = garoa, `61..67`/`80..82` = chuva, `71..77` = neve, `95..99` = tempestade). |
| `$wi(city)$` | Nome da cidade configurada. |
| `$wi(isday)$` | `1` durante o dia, `0` à noite. |

### 7.8 Astronomia e Dia/Noite — `ai()`

| Função | Retorno / Exemplo |
| :--- | :--- |
| `$ai(isday)$` | `1` durante o dia, `0` à noite. |
| `$ai(sunrise)$` / `$ai(sunset)$` | Horários de referência de nascer (`"06:00"`) e pôr do sol (`"18:00"`). |
| `$ai(season)$` | Estação do ano (`Verão`, `Outono`, `Inverno`, `Primavera`). |
| `$ai(mphase)$` | Fase lunar aproximada (`0..100`). |

### 7.9 Mídia — `mi()`

| Função | Retorno / Exemplo |
| :--- | :--- |
| `$mi(state)$` | `"PLAYING"` ou `"PAUSED"`. |
| `$mi(playing)$` | `1` tocando, `0` pausado. |

### 7.10 Variáveis Globais — `gv()`

- **Sintaxe:** `$gv(nome)$` ou `$gv(nome, valor_padrão)$`
- Retorna o valor atual da variável global configurada em **Globais (`$gv`)**, ou `valor_padrão` se ainda não existir.

### 7.11 Condicionais — `if()`

Suporta **if / else** simples ou cadeias **if / else if / else** ilimitadas:

- **Simples:** `$if(condição, valor_se_verdadeiro, valor_se_falso)$`
- **Encadeado:** `$if(c1, v1, c2, v2, c3, v3, padrão)$`
- **Exemplos:**
  - Texto de bateria: `$if(bi(charging), "⚡ Carregando", bi(level) < 20, "⚠ Crítica", bi(level) + "%")$`
  - Cor dinâmica (`fxColor`): `$if(bi(level) <= 20, "#FFFF5252", "#FFFFFFFF")$`
  - Ativo dinâmico (`fxAsset`): `$if(wi(code) >= 95, "tempestade", wi(code) >= 51, "chuva", ai(isday), "sol", "lua")$`

### 7.12 Manipulação de Cores — `ce()`

| Modo | Sintaxe | O que faz |
| :--- | :--- | :--- |
| `alpha` | `$ce("#FFFFFFFF", "alpha", 40)$` | Define transparência (`0–100%`) e retorna `#AARRGGBB`. |
| `mix` | `$ce("#FFFFFFFF", "mix", "#FF7AA7FF", 50)$` | Mistura duas cores pela porcentagem informada (`0–100`). |
| `contrast` | `$ce("#FF111318", "contrast")$` | Retorna `"#FF000000"` se a cor for clara ou `"#FFFFFFFF"` se for escura. |
| `lum` | `$ce("#FF808080", "lum")$` | Retorna a luminância percebida (`0..100`). |

### 7.13 Laços / Repetições — `fl()`

- **Sintaxe:** `$fl(início, fim, passo, "expressão_com_i", [separador])$` *(limite de 64 iterações)*
- **Exemplo (indicador de bateria em bolinhas):**
  `$fl(1, 5, 1, "if(i <= bi(level)/20, \"●\", \"○\")")$` → `●●●●○`

### 7.14 Texto e Formatação — `tc()`

| Modo | Sintaxe | Resultado |
| :--- | :--- | :--- |
| `up` / `upper` | `$tc(up, df(EEEE))$` | Tudo em MAIÚSCULAS. |
| `low` / `lower` | `$tc(low, "TEXTO")$` | Tudo em minúsculas. |
| `cap` / `capitalize` | `$tc(cap, df(EEEE))$` | Primeira Letra De Cada Palavra Maiúscula. |
| `len` | `$tc(len, "PrismLock")$` | Quantidade de caracteres (`9`). |
| `cut` | `$tc(cut, "PrismLock", 5)$` | Corta nos N primeiros caracteres (`"Prism"`). |
| `ell` | `$tc(ell, texto, 12)$` | Corta adicionando `"…"` se ultrapassar o limite. |
| `split` | `$tc(split, "A-B-C", "-", 1)$` | Divide pelo separador e pega o índice (`"B"`). |
| `lpad` / `rpad` | `$tc(lpad, 7, 2, "0")$` | Preenche à esquerda/direita até o tamanho (`"07"`). |
| `reg` | `$tc(reg, texto, padrão, subst)$` | Substituição com expressão regular (Regex). |
| `count` | `$tc(count, texto, sub)$` | Conta ocorrências de `sub` em `texto`. |

### 7.15 Funções Matemáticas — `mu()`

| Modo | Sintaxe | O que calcula |
| :--- | :--- | :--- |
| `min` / `max` | `$mu(min, bi(level), 50)$` | Menor / maior valor da lista. |
| `abs` | `$mu(abs, -12)$` | Valor absoluto (`12`). |
| `round` / `floor` / `ceil` | `$mu(round, 3.6)$` | Arredondamento (`4`, `3`, `4`). |
| `pow` / `sqrt` | `$mu(pow, 2, 3)$` / `$mu(sqrt, 81)$` | Potência (`8`) e raiz quadrada (`9`). |
| `sin` / `cos` | `$mu(sin, 90)$` | Seno e cosseno em **graus**. |
| `clamp` | `$mu(clamp, val, min, max)$` | Limita valor dentro do intervalo. |
| `rnd` | `$mu(rnd, 1, 10)$` | Inteiro aleatório entre mínimo e máximo. |

### 7.16 Tags Rápidas Legadas e Expressões `{=...}`

Mantidas com 100% de compatibilidade (e combináveis com sufixos `_UPPER` e `_LOWER`, ex.: `{EEEE_UPPER}`):

- **Relógio/Data:** `{HH}`, `{mm}`, `{ss}`, `{H}`, `{hh}`, `{a}`, `{EEEE}`, `{EEE}`, `{d}`, `{dd}`, `{MMMM}`, `{MMM}`, `{MM}`, `{yyyy/MM/dd}`, `{yyyy. MM. dd.}` (qualquer padrão `DateTimeFormatter` entre `{...}` também funciona!).
- **Telemetria rápida:** `{bat}`, `{charging}` (`⚡`), `{temp}`, `{cond}`, `{city}`, `{alarm}`, `{steps}`, `{dayprog}` (`0..100`), ou qualquer `{nome_da_global}`.
- **Expressão matemática `{=...}`:** suporta `+`, `-`, `*`, `/`, `%`, `^`, comparadores, ternário `cond ? a : b` e variáveis (`bat`, `charging`, `temp`, `steps`, `wcode`, `isday`, `hour`, `minute`, `second`, `day`, `month`, `dow`, `dayprog`, `hourprog`, `locked`, `pi`, além de nomes de globais numéricas e funções `sin`, `cos`, `tan`, `abs`, `sqrt`, `floor`, `ceil`, `round`, `min`, `max`, `clamp`).
  - Exemplo: `{=hour >= 18 ? 100 : 40}`

### 7.17 Lista Completa dos 58 Ícones Vetoriais Nativos

Chaves utilizáveis no campo `icon` ou retornadas por fórmulas `$if(...)$` em `fxAsset` (além da chave especial **`"weather"`**, que escolhe o ícone meteorológico automaticamente conforme `wi(code)` e `ai(isday)`):

- **Clima Completo (23):** `sun`, `moon`, `moon_crescent`, `cloud`, `cloud_sun`, `cloud_moon`, `clouds`, `rain`, `rain_heavy`, `drizzle`, `snow`, `sleet`, `hail`, `storm`, `wind`, `fog`, `humidity`, `thermometer`, `uv`, `sunrise`, `sunset`, `tornado`, `compass`.
- **Mídia (9):** `music`, `play`, `pause`, `skip_next`, `skip_prev`, `headphones`, `volume`, `mic`, `radio`.
- **Sistema & Conectividade (16):** `battery`, `battery_charging`, `bolt`, `alarm`, `clock`, `calendar`, `lock`, `unlock`, `wifi`, `bluetooth`, `flashlight`, `camera`, `bell`, `settings`, `phone`, `mail`.
- **Formas & Estilo (10):** `heart`, `steps`, `flame`, `location`, `star`, `sparkle`, `diamond`, `shield`, `home`, `search`.

---

## 8. Exportação e Importação Portátil (`.prism` e `.widget.prism`)

| Operação | Formato Gerado / Lido | O que vai empacotado | Como o PrismLock identifica |
| :--- | :--- | :--- | :--- |
| **Exportar Tela de Bloqueio** | `<nome>.prism` | `theme.json`, `bundle.json` (v2, `packageType = "WALLPAPER"`), fotos/vídeos de fundo (`assets/`), fontes usadas (`fonts/`), ativos nomeados (`custom_assets/`), imagens de camada (`layer_assets/`), globais e ações de toque. | Importa direto para a aba **Telas (Lock)** (`ProjectStore.upsertTheme`). |
| **Exportar Widget** | `<nome>.widget.prism` | `widget.json`, `theme.json`, `bundle.json` (v2, `packageType = "WIDGET"`, `isWidget = true`, `widgetAspect`), wallpaper de referência (`assets/`), ativos (`custom_assets/`, `layer_assets/`) e fontes (`fonts/`). | Identifica automaticamente por `widget.json`, `bundle.json` (`packageType = "WIDGET"` / `isWidget`), `isWidget` no JSON ou extensão `.widget.prism`, enviando direto para a aba **Widgets (KWGT)** (`ProjectStore.upsertWidget`). |
| **Importar Pasta** | Pasta SAF descompactada | Mesma estrutura de arquivos de um `.prism` extraído. | Lê `widget.json` ou `theme.json` + `bundle.json`, restaura fontes/mídias/ativos e roteia para **Telas** ou **Widgets**. |

---

## 9. Tutoriais Passo a Passo (Receitas Práticas)

### Tutorial 1 — Criando um Relógio em Cristal Óptico com Recorte 3D (Depth)

1. No Carrossel **Telas (Lock)**, toque em **`+`** e dê um nome ao tema.
2. No painel inferior do Estúdio, toque na pílula **`Fundo & 3D`** → **`Foto / Vídeo`** e escolha uma foto de retrato/objeto nítido (ou **`Pasta Carrossel SAF`**).
3. Confira na miniatura que o selo do canto superior direito está **`3D ON`** e ajuste, se quiser, `Bokeh Fundo` ou `Modo Retrato`. Toque em **`Voltar às Abas`**.
4. Na aba **Camadas**, confira se existe a camada **Sujeito 3D** (`Profundidade 3D`) **abaixo** da lista depois do relógio (na árvore de cima para baixo, camadas *abaixo* são desenhadas *na frente*). Se precisar, use `↑` / `↓`.
5. Selecione a camada de **Hora** (`Texto/Código`).
6. Vá para **Forma & Fonte**, aumente o **Tamanho** (ex.: `280px`) e ajuste a **Elasticidade** (ex.: `1.8x`).
7. Vá para **Vidro & Cor** → selecione **`Cristal Óptico`** e toque no preset **`Cristal Limpo`** (ajuste `Refração` e `Chanfro` a gosto).
8. Arraste o relógio no canvas até que parte da base das letras fique sutilmente atrás do sujeito recortado.

### Tutorial 2 — Trocando SVGs ou PNGs pelo Clima (`$if(...)$` + Ativos Nomeados)

1. Em **Camadas**, adicione uma camada **`Ícone`** ou **`Imagem / Ativo`**.
2. Em **Conteúdo** → seção **`Biblioteca Visual de Ativos Nomeados`**, toque em **`+ Adicionar Ativo`**, digite `"sol"` e selecione um arquivo `.svg` ou `.png` de sol.
3. Repita o processo adicionando `"chuva"` e `"lua"`.
4. Ainda em **Conteúdo**, toque no botão **`ƒx`** da linha **`Ativo / Ícone Dinâmico (fxAsset)`**.
5. Digite a fórmula usando os atalhos:
   `$if(wi(code) >= 51, "chuva", ai(isday) = 1, "sol", "lua")$`
6. Toque em **Confirmar**. Sempre que chover na cidade configurada em **Clima & Automação** (ou anoitecer), o ativo troca sozinho — e se for uma `Imagem / Ativo`, você ainda pode ir em **Vidro & Cor** e escolher **Cristal Óptico** para aplicar refração seguindo a silhueta alfa do PNG!

### Tutorial 3 — Painel Interativo por Toque com Globais (`$gv$`)

1. Toque na pílula **`Globais ($gv)`** → **`+ Interruptor`** e crie uma global chamada `painel` (valor inicial `0`).
2. Clique em **`Voltar às Abas`**. Crie ou selecione um Card de informações (ou uma Pilha inteira).
3. Na aba **Posição & Toque** do Card:
   - Role até **Visibilidade da Camada** e toque em **`ƒx`** ao lado de `visibilityExpr`.
   - Insira `$gv(painel) = 1$` e confirme (o card agora só aparece quando `painel` estiver ligado).
4. Agora crie um botão/ícone pequeno que ficará sempre visível:
   - Na aba **Posição & Toque** desse botão, vá em **Ação ao Clicar** e escolha **`Alternar Global`**.
   - Selecione a global `painel`.
5. Ative o chip **`Testar Toques na Tela`** no início da seção de Toque e clique no botão no canvas para ver o Card aparecer e desaparecer instantaneamente!

### Tutorial 4 — Criando, Fixando e Exportando um Widget Independente (`.widget.prism`)

1. No Carrossel Principal, toque na pílula central **`Widgets (KWGT)`** e depois no botão branco **`+`**.
2. Dê um nome (ex.: `Relógio Vidro`) e o Estúdio abrirá com a moldura do widget.
3. Toque na pílula **`Formato & Fundo`**, escolha **`Card 4×2`** (ou outro formato) e toque em **`Wallpaper de Referência (Refração)`** para selecionar o fundo do seu aparelho.
4. Personalize suas camadas de texto elástico, barras de bateria ou cristal óptico (dica: experimente `Modelos Prontos` em **Camadas**).
5. Para fixar na Tela Inicial: abra **`⋮`** no topo direito e toque em **`Aplicar & Fixar Widget`** (ou toque no ícone de pino `📌` no Carrossel).
6. Para compartilhar ou fazer backup: abra **`⋮`** → **`Exportar Pacote (.prism)`**; o arquivo será salvo como `<nome>.widget.prism` com todas as fontes, proporção, ativos e configurações preservados.

---

## 10. Modelos Prontos por Estilo

Os modelos embarcados (Carrossel na primeira abertura e botão **`Modelos Prontos`** na aba **Camadas**) são organizados por estilo, cada um com foco e público próprios. Nenhum usa nome de marca.

### Telas de Bloqueio

| Modelo | Visual | Para quem |
| :--- | :--- | :--- |
| **Moderno · Profundidade** | Bebas Neue gigante em tom areia, sujeito 3D da foto à frente do relógio | Fotos de pessoas, pets e objetos |
| **Clean · Névoa** | Inter fina centralizada, muito respiro, sem efeitos | Minimalistas; a foto em destaque |
| **Fofo · Pêssego** | Plus Jakarta arredondada em degradê pêssego→rosa, chip com coração e brilho, pílula pastel de bateria | Estética soft / cute |
| **Vidro · Aurora** | Relógio alto em Cristal Óptico elástico + recorte 3D | Quem quer o efeito de refração |
| **Elegante · Editorial** | Playfair Display em creme, regra fina, caixa-alta espaçada | Estética editorial, paisagens e moda |
| **Avançado · Terminal** | JetBrains Mono sobre vidro escuro com barras de bateria e progresso do dia, clima e alarme | Power users / dados |
| **Simples · Canto** | Relógio discreto no canto inferior | Quem quer a foto inteira visível |
| **Ousado · Ácido** | Syne pesada em degradê lima↔violeta | Estética Y2K / streetwear |

### Widgets

| Modelo | Visual |
| :--- | :--- |
| **Clean · Relógio** | Pílula de vidro fosco claro com hora fina e data |
| **Moderno · Cristal Elástico** | Relógio esticado em cristal óptico sobre placa de vidro |
| **Fofo · Energia** | Cartão pastel com anel de bateria rosa (verde ao carregar) |
| **Avançado · Painel** | Monoespaçada com bateria, dia, clima e alarme em barras |
| **Música · Cápsula** | Toque para tocar/pausar; ícone acompanha o estado da mídia |
| **Elegante · Calendário** | Dia em serifada grande, semana e mês; toque abre o Alarme |

> Cópias salvas de modelos de versões anteriores que ainda têm o título original recebem o novo visual automaticamente (id, fotos, pasta e ajustes preservados). Temas criados ou renomeados pelo usuário nunca são alterados.

---

## 11. Desempenho, Memória e Bateria

Medições em um Galaxy QHD+ (Android 16 / One UI), app release:

| Cenário | Antes | Agora |
| :--- | :--- | :--- |
| Processo `:wallpaper` com tela apagada ou desbloqueado em outro app | 373 MB PSS | ~56 MB PSS (≈20 MB sem cena carregada) |
| App aberto no Hub | 353 MB PSS | 97 MB PSS |
| CPU no Hub / em segundo plano | ~185% contínuo | 0% em repouso (0 quadros desenhados parado) |

**Como isso é feito**

- **Apresentação própria (`HardwareRenderer`)**: o wallpaper desenha num `RenderNode` próprio em vez de `lockHardwareCanvas`. O renderizador é parado 1,5 s após o último quadro e destruído quando o wallpaper não está visível; o HWUI então libera contexto de GPU, cache Skia e render targets. O último quadro continua na tela (o SurfaceFlinger mantém o buffer).
- **Hibernação**: 3 s depois de ficar invisível ou de apagar a tela, o processo `:wallpaper` libera foto, fundo fosco, máscara 3D, caches de forma/fonte e renderizadores. Com o aparelho bloqueado mantém apenas o quadro final (`RGB_565`, ~5 MB) para acender sem atraso; desbloqueado em outro app não mantém nada. Qualquer remontagem (acender, virada de minuto) recarrega a foto a partir do bake em disco.
- **Zero trabalho com tela apagada**: ticks de minuto, sensores, vídeo e animações só rodam com o wallpaper visível.
- **Hub sem loop de recomposição**: os `Flow` do DataStore são criados uma vez (`remember`) e coletados com `collectAsStateWithLifecycle` (nada é coletado com o app parado); os modelos padrão são memoizados. As prévias dos cards são guardadas em disco (WebP) com chave = projeto + imagem exibida (uri, data e tamanho do arquivo) + dia, e invalidadas quando o carrossel avança.
- **Widgets**: cada atualização carrega a imagem de referência sem cache, recicla após o render e libera o renderizador; sem imagem própria usa fundo neutro.
- **Release com R8**: `optimization { enable = true }` + `app/proguard-rules.pro` com keep rules do ML Kit (sem elas o primeiro `InputImage.fromBitmap()` falhava). Falhas de segmentação 3D degradam para "sem máscara" em vez de fechar o app.
- **Sem telemetria em segundo plano**: o `TransportBackendDiscovery` (datatransport do ML Kit) é removido no manifest; o `DefaultScheduler` descarta os eventos sem persistir nem agendar job, então o app nunca é acordado para enviar logs.
