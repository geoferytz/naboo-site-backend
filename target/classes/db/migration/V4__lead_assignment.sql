alter table inquiries add column assigned_user_id uuid references users(id);
alter table demo_requests add column assigned_user_id uuid references users(id);
create index idx_inquiries_assignee on inquiries(assigned_user_id);
create index idx_demos_assignee on demo_requests(assigned_user_id);
