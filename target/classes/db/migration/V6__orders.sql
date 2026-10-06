create table orders (
    id uuid primary key,
    reference_number varchar(32) not null unique,
    full_name varchar(160) not null,
    email varchar(254) not null,
    phone varchar(40) not null,
    company varchar(160),
    payment_method varchar(20) not null,
    payment_reference varchar(80),
    payer_phone varchar(40),
    currency varchar(8) not null,
    total_amount numeric(19,2) not null,
    status varchar(30) not null default 'AWAITING_PAYMENT',
    notes text,
    created_at timestamp with time zone not null default now(),
    paid_at timestamp with time zone
);
create table order_items (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete cascade,
    package_id uuid not null references pricing_packages(id),
    product_name varchar(160) not null,
    package_name varchar(120) not null,
    billing_frequency varchar(20) not null,
    unit_price numeric(19,2) not null,
    quantity integer not null default 1
);
create index idx_orders_status on orders(status, created_at);
create index idx_order_items_order on order_items(order_id);
