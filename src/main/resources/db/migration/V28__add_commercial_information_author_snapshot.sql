alter table company_commercial_information
    add column if not exists author_name varchar(255),
    add column if not exists author_email varchar(255);

update company_commercial_information info
set author_name = users.name,
    author_email = users.email
from users
where info.author_user_id = users.id
  and (info.author_name is null or info.author_email is null);
