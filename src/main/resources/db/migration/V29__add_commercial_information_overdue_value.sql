alter table company_commercial_information
    add column if not exists overdue_value varchar(50);
