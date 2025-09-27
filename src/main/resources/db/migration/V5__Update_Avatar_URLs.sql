-- Script SQL để cập nhật tất cả các S3 key thành URL đầy đủ trong bảng user_profiles
UPDATE user_profiles
SET avt_url = CONCAT('https://taskflowprojectteam123.s3.ap-southeast-2.amazonaws.com/', avt_url)
WHERE avt_url IS NOT NULL
AND avt_url NOT LIKE 'http%'
AND (avt_url LIKE 'task-files/%' OR avt_url LIKE 'avatars/%');

