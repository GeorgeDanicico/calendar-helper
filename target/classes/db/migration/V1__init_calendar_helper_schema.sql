create table app_user (
	id uuid default random_uuid() primary key,
	email varchar(320) not null unique,
	created_at timestamp not null default current_timestamp
);

create table oauth_connections (
	id uuid default random_uuid() primary key,
	user_id uuid not null,
	provider_subject varchar(255) not null,
	access_token text,
	refresh_token text,
	access_token_expires_at timestamp,
	scope text,
	connection_type varchar(100) not null,
	created_at timestamp not null default current_timestamp,
	updated_at timestamp not null default current_timestamp,
	constraint fk_google_oauth_connection_user
		foreign key (user_id) references app_user (id),
	constraint uq_oauth_connections_type_subject
		unique (connection_type, provider_subject),
	constraint uq_oauth_connections_user_type
		unique (user_id, connection_type)
);

create table gmail_latest_messages (
	id uuid default random_uuid() primary key,
	user_id uuid not null,
	last_seen_message_id varchar(255) not null,
	processed_at timestamp not null default current_timestamp,
	constraint fk_gmail_message_scan_user
		foreign key (user_id) references app_user (id),
	constraint uq_gmail_latest_messages_user
		unique (user_id)
);
