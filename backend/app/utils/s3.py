import boto3
from botocore.exceptions import ClientError
from ..config import settings
import logging

logger = logging.getLogger(__name__)

class S3Service:
    def __init__(self):
        self.s3_client = boto3.client(
            's3',
            aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
            aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
            region_name=settings.AWS_REGION
        )
        self.bucket_name = settings.S3_BUCKET_NAME
        self.cdn_domain = settings.CLOUDFRONT_DOMAIN.rstrip('/')

    def _upload_file(self, file_content, object_name, content_type):
        """
        Internal helper to upload a file to S3.
        """
        try:
            self.s3_client.put_object(
                Bucket=self.bucket_name,
                Key=object_name,
                Body=file_content,
                ContentType=content_type
            )
            return f"{self.cdn_domain}/{object_name}"
        except ClientError as e:
            logger.error(f"Failed to upload file to S3: {e}")
            raise Exception(f"S3 upload failed: {str(e)}")

    def upload_video(self, file_content, folder: str, filename: str):
        """
        Upload a wedding video to S3.
        """
        object_name = f"videos/{folder}/{filename}"
        url = self._upload_file(file_content, object_name, "video/mp4")
        return {"url": url}

    def upload_photo(self, file_content, folder: str, filename: str):
        """
        Upload an album photo to S3.
        """
        object_name = f"photos/{folder}/{filename}"
        # Typically photos are jpeg/png
        content_type = "image/jpeg"
        if filename.lower().endswith(".png"):
            content_type = "image/png"
        
        url = self._upload_file(file_content, object_name, content_type)
        return {"url": url}

s3_service = S3Service()
