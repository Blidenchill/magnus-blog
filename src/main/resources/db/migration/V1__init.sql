-- Magnus Blog V1 schema
CREATE TABLE site_profile (
    id              uuid PRIMARY KEY,
    name            varchar(120) NOT NULL,
    bio             text,
    avatar_media_id uuid,
    contact         varchar(500),
    version         bigint NOT NULL DEFAULT 0,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE content_entry (
    id                    uuid PRIMARY KEY,
    type                  varchar(16) NOT NULL CHECK (type IN ('PHOTO', 'BLOG', 'FOOD', 'TRAVEL')),
    slug                  varchar(80) NOT NULL UNIQUE,
    draft_revision_id     uuid,
    published_revision_id uuid,
    first_published_at    timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    deleted_at            timestamptz,
    version               bigint NOT NULL DEFAULT 0
);

CREATE TABLE content_revision (
    id             uuid PRIMARY KEY,
    entry_id       uuid NOT NULL REFERENCES content_entry (id) ON DELETE CASCADE,
    kind           varchar(10) NOT NULL CHECK (kind IN ('DRAFT', 'PUBLISHED')),
    title          varchar(120) NOT NULL DEFAULT '',
    summary        varchar(500),
    category       varchar(60),
    body_html      text,
    cover_media_id uuid,
    featured       boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    version        bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_revision_entry ON content_revision (entry_id);

ALTER TABLE content_entry
    ADD CONSTRAINT fk_entry_draft FOREIGN KEY (draft_revision_id) REFERENCES content_revision (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_entry_published FOREIGN KEY (published_revision_id) REFERENCES content_revision (id) ON DELETE SET NULL;

CREATE TABLE content_tag (
    revision_id uuid NOT NULL REFERENCES content_revision (id) ON DELETE CASCADE,
    tag         varchar(24) NOT NULL,
    PRIMARY KEY (revision_id, tag)
);

CREATE TABLE media_asset (
    id         uuid PRIMARY KEY,
    filename   varchar(200) NOT NULL,
    mime       varchar(60) NOT NULL,
    sha256     varchar(64) NOT NULL,
    width      int NOT NULL,
    height     int NOT NULL,
    status     varchar(12) NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE media_variant (
    id           uuid PRIMARY KEY,
    media_id     uuid NOT NULL REFERENCES media_asset (id) ON DELETE CASCADE,
    variant_type varchar(10) NOT NULL CHECK (variant_type IN ('ORIGINAL', 'DISPLAY', 'THUMB')),
    width        int NOT NULL,
    height       int NOT NULL,
    path         varchar(300) NOT NULL,
    mime         varchar(60) NOT NULL,
    size_bytes   bigint NOT NULL,
    UNIQUE (media_id, variant_type)
);

ALTER TABLE site_profile
    ADD CONSTRAINT fk_profile_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_asset (id) ON DELETE SET NULL;
ALTER TABLE content_revision
    ADD CONSTRAINT fk_revision_cover FOREIGN KEY (cover_media_id) REFERENCES media_asset (id) ON DELETE SET NULL;

CREATE TABLE revision_media_ref (
    revision_id uuid NOT NULL REFERENCES content_revision (id) ON DELETE CASCADE,
    media_id    uuid NOT NULL REFERENCES media_asset (id) ON DELETE RESTRICT,
    role        varchar(16) NOT NULL CHECK (role IN ('COVER', 'BODY', 'PHOTO', 'STEP', 'DESTINATION')),
    PRIMARY KEY (revision_id, media_id, role)
);
CREATE INDEX idx_ref_media ON revision_media_ref (media_id);

CREATE TABLE photo_item (
    id          uuid PRIMARY KEY,
    revision_id uuid NOT NULL REFERENCES content_revision (id) ON DELETE CASCADE,
    media_id    uuid NOT NULL REFERENCES media_asset (id) ON DELETE RESTRICT,
    sort_order  int NOT NULL,
    alt_text    varchar(300),
    caption     varchar(500),
    UNIQUE (revision_id, sort_order)
);

CREATE TABLE recipe_detail (
    revision_id      uuid PRIMARY KEY REFERENCES content_revision (id) ON DELETE CASCADE,
    difficulty       varchar(20),
    duration_minutes int,
    servings         varchar(30),
    intro            text
);

CREATE TABLE recipe_ingredient (
    id          uuid PRIMARY KEY,
    revision_id uuid NOT NULL REFERENCES content_revision (id) ON DELETE CASCADE,
    name        varchar(120) NOT NULL,
    amount      varchar(120),
    sort_order  int NOT NULL,
    UNIQUE (revision_id, sort_order)
);

CREATE TABLE recipe_step (
    id          uuid PRIMARY KEY,
    revision_id uuid NOT NULL REFERENCES content_revision (id) ON DELETE CASCADE,
    text        text,
    media_id    uuid REFERENCES media_asset (id) ON DELETE RESTRICT,
    alt_text    varchar(300),
    sort_order  int NOT NULL,
    UNIQUE (revision_id, sort_order)
);

CREATE TABLE travel_destination (
    id               uuid PRIMARY KEY,
    revision_id      uuid NOT NULL REFERENCES content_revision (id) ON DELETE CASCADE,
    name             varchar(160) NOT NULL,
    region           varchar(120),
    summary          text,
    recommend_reason text,
    visit_date       date,
    map_url          varchar(500),
    media_id         uuid REFERENCES media_asset (id) ON DELETE RESTRICT,
    alt_text         varchar(300),
    sort_order       int NOT NULL,
    UNIQUE (revision_id, sort_order)
);

CREATE TABLE auth_event (
    id         uuid PRIMARY KEY,
    username   varchar(120),
    ip         varchar(64),
    success    boolean NOT NULL,
    type       varchar(24) NOT NULL,
    user_agent varchar(300),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_event_created ON auth_event (created_at DESC);

-- Public listing queries: by type, newest first, not deleted, has published pointer
CREATE INDEX idx_entry_public ON content_entry (type, first_published_at DESC, id DESC)
    WHERE deleted_at IS NULL AND published_revision_id IS NOT NULL;
CREATE INDEX idx_entry_deleted ON content_entry (deleted_at) WHERE deleted_at IS NOT NULL;
