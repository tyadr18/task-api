# task-api

> A small task management REST API built with Spring Boot 3, JPA and H2 (switchable to PostgreSQL), with CRUD, filtering, pagination, validation and JSON error responses — covered by unit and MockMvc tests.

[![test](https://github.com/tyadr18/task-api/actions/workflows/test.yml/badge.svg)](https://github.com/tyadr18/task-api/actions/workflows/test.yml)

## 概要

タスクの登録・一覧・更新・削除ができるREST APIです。ポートフォリオ用に作った作例で、実在の案件とは関係ありません。

小さな構成ですが、次の3点を優先しました。

- 動くこと：クローンして `mvn spring-boot:run` するだけで、DBを用意しなくても起動します
- テストがあること：サービス層の単体テスト、Web層のMockMvcテスト、アプリ全体を起動する結合テストの3段で確認しています
- 読みやすいこと：層ごとにパッケージを分け、設計上の判断はコード中のコメントにも残しています

主な機能

- タスクのCRUD（タイトル・説明・ステータス・優先度・期限日）
- ステータス（`TODO` / `DOING` / `DONE`）と優先度（`LOW` / `MEDIUM` / `HIGH`）での絞り込み
- ページネーションと並べ替え
- 入力チェックと、エラー時の統一JSON（400 / 404 / 405 / 415 / 500）

## 使った技術

| 分類 | 内容 |
| --- | --- |
| 言語 | Java 21 |
| フレームワーク | Spring Boot 3.5.16（Spring Web / Spring Data JPA / Bean Validation） |
| DB | H2（既定・インメモリ）、PostgreSQL（`postgres` プロファイル） |
| ビルド | Maven |
| テスト | JUnit 5 / Mockito / MockMvc / AssertJ |
| CI | GitHub Actions（H2での全テストと、PostgreSQLでの結合テスト） |

Spring Boot 3系の最終版である 3.5.16 を使っています。3.5系の無償サポートは2026年6月で終了しているため、実運用で使う場合は 4.x への移行を検討してください。

## 動かし方

### 必要なもの

- JDK 21
- Maven 3.9 以上

### 1. クローンして起動する（H2）

```bash
git clone https://github.com/tyadr18/task-api.git
cd task-api
mvn spring-boot:run
```

`http://localhost:8080` で起動します。データはメモリ上にあるので、止めると消えます。ポートを変えたいときは環境変数 `PORT` を指定してください。

### 2. 試しに使ってみる

```bash
# 作成
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"見積書を作る","priority":"HIGH","dueDate":"2026-12-10"}'

# 一覧（TODOだけ、期限の近い順に5件）
curl "http://localhost:8080/api/tasks?status=TODO&sort=dueDate,asc&size=5"

# 更新（PUTは全項目の置き換え）
curl -X PUT http://localhost:8080/api/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"見積書を送る","status":"DONE","priority":"MEDIUM"}'

# 削除
curl -i -X DELETE http://localhost:8080/api/tasks/1
```

Windows の PowerShell では `curl.exe` を使うか、`Invoke-RestMethod` で同じリクエストを送れます。

### 3. テストを実行する

```bash
mvn test
```

### 4. PostgreSQL で動かす（任意）

接続情報はコードに書かず、環境変数 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` から読み込みます。

```bash
cp .env.example .env          # 値を自分の環境に合わせて書き換える
docker compose up -d          # PostgreSQL を起動（Docker を使う場合）

# macOS / Linux
set -a; source .env; set +a
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

```powershell
# Windows (PowerShell)
$env:DB_URL = "jdbc:postgresql://localhost:5432/taskdb"
$env:DB_USERNAME = "taskapi"
$env:DB_PASSWORD = "change-me"
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

テーブルは起動時に自動で作られます。

## 工夫点

- **優先度は数値で保存して、意味どおりに並べ替えられるようにした**
  enum を文字列で保存すると、並べ替えが `HIGH → LOW → MEDIUM` の辞書順になってしまいます。`AttributeConverter` で `LOW=1 / MEDIUM=2 / HIGH=3` に変換して保存し、`sort=priority,desc` で高い順に並ぶようにしました。結合テストでもこの順序を確認しています。
- **並べ替えに使える項目を限定した**
  `sort` に存在しない項目を渡すと、そのままでは500エラーになります。許可した項目（`id`, `title`, `priority`, `dueDate`, `createdAt`, `updatedAt`）以外は400で返し、指定できる項目をメッセージで案内します。
- **エラーの形をそろえた**
  404・400・405などをすべて同じ形のJSONで返します。入力チェックのエラーは項目ごとに `errors` に入ります。想定外の例外は内部情報を返さず、ログにだけ詳細を残します。
- **エンティティを直接返さない**
  レスポンスは `TaskResponse` に詰め替えて返します。一覧も Spring Data の `Page` をそのままJSONにせず、必要な項目だけの `PageResponse` にしているので、ライブラリの更新でレスポンスの形が変わりません。
- **更新直後の `updatedAt` を正しく返す**
  `@UpdateTimestamp` はDBへの反映時に値が入るため、更新時は `saveAndFlush` で反映してから返しています。こうしないと、レスポンスの `updatedAt` が更新前の値のままになります。
- **作成と更新でルールを分けた**
  作成では `status` と `priority` を省略でき、それぞれ `TODO` / `MEDIUM` になります。更新（PUT）は全項目の置き換えなので、うっかり値を消さないよう `status` と `priority` を必須にしています。
- **3段のテストとCI**
  サービス層はMockitoで、Web層は `@WebMvcTest` で、全体は `@SpringBootTest` で確認しています。GitHub Actions では H2 での全テストに加えて、PostgreSQL のコンテナを立てて同じ結合テストを流し、プロファイルを切り替えても動くことを確かめています。

## ディレクトリ構成

```
task-api/
├── .github/workflows/test.yml       # CI（H2 と PostgreSQL でテスト）
├── compose.yaml                     # ローカル確認用の PostgreSQL
├── .env.example                     # 接続情報のサンプル
├── pom.xml
└── src
    ├── main
    │   ├── java/io/github/tyadr18/taskapi
    │   │   ├── TaskApiApplication.java
    │   │   ├── domain        # Task エンティティ、ステータス・優先度、優先度の変換
    │   │   ├── repository    # TaskRepository、絞り込み条件（Specification）
    │   │   ├── service       # TaskService（業務ロジック・並べ替え項目のチェック）
    │   │   ├── web           # TaskController
    │   │   ├── dto           # リクエスト／レスポンス、ページネーション
    │   │   └── exception     # 例外と共通エラーハンドラー
    │   └── resources
    │       ├── application.yml            # 既定（H2）
    │       └── application-postgres.yml   # PostgreSQL 用
    └── test/java/io/github/tyadr18/taskapi
        ├── service/TaskServiceTest.java       # サービス層の単体テスト
        ├── web/TaskControllerTest.java        # MockMvc による Web 層のテスト
        └── TaskApiIntegrationTest.java        # アプリ全体の結合テスト
```

## APIエンドポイント一覧

ベースURL：`http://localhost:8080`

| メソッド | パス | 内容 | 成功時 |
| --- | --- | --- | --- |
| `POST` | `/api/tasks` | タスクを作成 | `201 Created`（`Location` ヘッダー付き） |
| `GET` | `/api/tasks` | 一覧を取得（絞り込み・ページネーション） | `200 OK` |
| `GET` | `/api/tasks/{id}` | 1件取得 | `200 OK` |
| `PUT` | `/api/tasks/{id}` | 全項目を更新 | `200 OK` |
| `DELETE` | `/api/tasks/{id}` | 削除 | `204 No Content` |

### 一覧のクエリパラメータ

| パラメータ | 例 | 説明 |
| --- | --- | --- |
| `status` | `TODO` | ステータスで絞り込み（`TODO` / `DOING` / `DONE`） |
| `priority` | `HIGH` | 優先度で絞り込み（`LOW` / `MEDIUM` / `HIGH`） |
| `page` | `0` | ページ番号（0始まり、既定 0） |
| `size` | `20` | 1ページの件数（既定 20、上限 100） |
| `sort` | `dueDate,asc` | 並べ替え。`id` / `title` / `priority` / `dueDate` / `createdAt` / `updatedAt`（既定 `id,asc`） |

値は大文字で指定します（`todo` は不可）。

### リクエストボディ

| 項目 | 型 | 作成（POST） | 更新（PUT） |
| --- | --- | --- | --- |
| `title` | 文字列 | 必須・100文字以内 | 必須・100文字以内 |
| `description` | 文字列 | 任意・1000文字以内 | 任意（省略すると空になる） |
| `status` | `TODO` / `DOING` / `DONE` | 任意（既定 `TODO`） | 必須 |
| `priority` | `LOW` / `MEDIUM` / `HIGH` | 任意（既定 `MEDIUM`） | 必須 |
| `dueDate` | `YYYY-MM-DD` | 任意 | 任意（省略すると空になる） |

### レスポンス例

タスク

```json
{
  "id": 1,
  "title": "見積書を作る",
  "description": null,
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2026-12-10",
  "createdAt": "2026-11-20T03:00:00.123Z",
  "updatedAt": "2026-11-20T03:00:00.123Z"
}
```

一覧

```json
{
  "content": [ { "id": 1, "title": "見積書を作る", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

エラー（入力チェック）

```json
{
  "timestamp": "2026-11-20T03:00:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "入力内容に誤りがあります",
  "path": "/api/tasks",
  "errors": [
    { "field": "title", "message": "タイトルは必須です" }
  ]
}
```

| ステータス | 主な発生条件 |
| --- | --- |
| `400` | 入力チェック違反、JSONの形式違い、存在しない `status` / `priority`、数値でないID、許可していない `sort` |
| `404` | 指定したIDのタスクがない、存在しないURL |
| `405` | パスに対応していないメソッド（`Allow` ヘッダーで使えるメソッドを返す） |
| `415` | `Content-Type` が `application/json` でない |
| `500` | 想定外のエラー（詳細はサーバーログのみ） |

## 今後やりたいこと

- Flyway によるスキーマ管理（現在はテーブルを自動生成）
- 楽観ロック（`@Version`）による同時更新の検知
- OpenAPI（Swagger UI）でのAPIドキュメント公開

## ライセンス

MIT
