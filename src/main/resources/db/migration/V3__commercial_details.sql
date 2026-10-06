alter table pricing_packages
    add column user_limit varchar(80),
    add column room_limit varchar(80),
    add column product_limit varchar(80),
    add column support_level varchar(80);

alter table services
    add column category varchar(100) not null default 'General',
    add column price_type varchar(30) not null default 'QUOTE',
    add column starting_price numeric(19,2),
    add column currency varchar(8) not null default 'TSh';

update pricing_packages p set user_limit='1', product_limit='500', support_level='Basic'
where p.name='Starter' and exists(select 1 from products where products.id=p.product_id and products.slug='sales-management');
update pricing_packages p set user_limit='5', product_limit='Unlimited', support_level='Priority'
where p.name='Business' and exists(select 1 from products where products.id=p.product_id and products.slug='sales-management');
update pricing_packages p set user_limit='Custom', product_limit='Unlimited', support_level='Dedicated'
where p.name='Enterprise' and exists(select 1 from products where products.id=p.product_id and products.slug='sales-management');
update pricing_packages p set room_limit='10', support_level='Basic'
where p.name='Basic' and exists(select 1 from products where products.id=p.product_id and products.slug='hotel-management');
update pricing_packages p set room_limit='50', support_level='Priority'
where p.name='Professional' and exists(select 1 from products where products.id=p.product_id and products.slug='hotel-management');
update pricing_packages p set room_limit='Unlimited', support_level='Dedicated'
where p.name='Enterprise' and exists(select 1 from products where products.id=p.product_id and products.slug='hotel-management');

update services set category='Software Development', price_type='QUOTE' where slug='custom-software';
update services set category='Web Development', price_type='QUOTE' where slug='website-development';
update services set category='Implementation', price_type='FROM', starting_price=50000 where slug='software-installation';
update services set category='Hosting', price_type='QUOTE' where slug='cloud-hosting';
update services set category='Consultation', price_type='QUOTE' where slug='it-consultation';
update services set category='Support', price_type='PACKAGE' where slug='technical-support';
