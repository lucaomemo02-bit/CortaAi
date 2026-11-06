# CortaAi — editor de vídeo para Android

App nativo (Kotlin + Jetpack Compose + Media3). A exportação usa os codificadores de hardware
do aparelho em fluxo, sem carregar o vídeo inteiro na memória, então aceita arquivos grandes e longos.

## O que tem

- Importar vários vídeos e fotos de uma vez (seletor de fotos do Android, sem permissões)
- Linha do tempo com miniaturas, reordenar, duplicar, excluir
- Cortar (início/fim), dividir no ponto do player
- Velocidade 0.25x a 4x (vídeo e áudio)
- Volume por clipe (0–200%) e música de fundo
- Filtros prontos + brilho, contraste, saturação e temperatura
- Girar 90°
- Texto na tela
- Exportar em H.264/AAC: formato original, 9:16, 16:9 ou 1:1, até 4K
- Salva em Filmes/CortaAi na galeria

## Como gerar o APK sem Android Studio

1. Crie um repositório no GitHub e suba esta pasta inteira (inclusive `.github`).
2. Vá na aba **Actions → Build APK → Run workflow** (ou é automático a cada push).
3. Quando terminar, baixe o artefato **CortaAi-apk**, extraia o `.apk` e instale no celular.

## Como gerar no Android Studio

Abra a pasta, deixe o Gradle sincronizar e rode `app`.

## Limitações conhecidas

- A exportação roda com o app aberto (sem serviço em segundo plano ainda).
- Texto: fixo no clipe inteiro, só um estilo, posição embaixo.
- Sem transições, keyframes, legendas automáticas, remoção de fundo, etc.
- 4K e vídeos muito longos dependem do codificador do aparelho.
- O código ainda não foi compilado nem testado em aparelho: o primeiro build pode apontar ajustes.
