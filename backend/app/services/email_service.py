import logging
import aiosmtplib
from email.message import EmailMessage
from ..config import settings

logger = logging.getLogger(__name__)


class EmailService:
    def __init__(self):
        self.host = settings.SMTP_HOST
        self.port = settings.SMTP_PORT
        self.user = settings.SMTP_USER
        self.password = settings.SMTP_PASSWORD
        self.sender = settings.SMTP_FROM_EMAIL or "noreply@weddingmemory.com"

        self.is_configured = bool(self.host and self.port and self.user and self.password)
        if not self.is_configured:
            logger.warning("Email service is NOT configured. Emails will be printed to console.")

    async def send_reset_email(self, to_email: str, reset_url: str):
        """Send the access code reset email."""
        
        subject = "Reset Your Wedding Album Access Code"
        
        # HTML Email Template
        html_content = f"""
        <html>
            <body style="font-family: 'Nunito Sans', sans-serif; background-color: #F8F9FA; color: #2D3748; padding: 20px;">
                <div style="max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border-top: 5px solid #9D84C7;">
                    <h2 style="color: #2D3748; margin-top: 0;">Wedding Memory</h2>
                    <p style="font-size: 16px; line-height: 1.5; color: #4A5568;">
                        You requested a link to reset your wedding album access code. Click the button below to set a new code.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{reset_url}" style="display: inline-block; background-color: #9D84C7; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px;">
                            Reset Access Code
                        </a>
                    </div>
                    <p style="font-size: 14px; line-height: 1.5; color: #718096;">
                        If you didn't request this, you can safely ignore this email. This link will expire in 15 minutes.
                    </p>
                </div>
            </body>
        </html>
        """

        if not self.is_configured:
            # Fallback to console for local development
            print(f"\n{'='*60}")
            print(f"📧 EMAIL MOCK (to: {to_email})")
            print(f"Subject: {subject}")
            print(f"URL: {reset_url}")
            print(f"{'='*60}\n")
            return

        message = EmailMessage()
        message["From"] = self.sender
        message["To"] = to_email
        message["Subject"] = subject
        message.add_alternative(html_content, subtype="html")

        try:
            await aiosmtplib.send(
                message,
                hostname=self.host,
                port=self.port,
                username=self.user,
                password=self.password,
                use_tls=self.port in (465, "465"),
                start_tls=self.port in (587, "587"),
            )
            logger.info("Reset email sent to %s", to_email)
        except Exception as e:
            logger.error("Failed to send email to %s: %s", to_email, str(e))


email_service = EmailService()
