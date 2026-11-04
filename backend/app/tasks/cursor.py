import base64
from datetime import datetime
from uuid import UUID


class InvalidTaskCursor(ValueError):
    """The caller supplied an invalid or unsupported task cursor."""


def encode_task_cursor(created_at: datetime, task_id: UUID) -> str:
    if created_at.tzinfo is None:
        raise ValueError("Task cursor timestamps must be timezone-aware.")
    value = f"{created_at.isoformat()}|{task_id}"
    return base64.urlsafe_b64encode(value.encode("utf-8")).rstrip(b"=").decode("ascii")


def decode_task_cursor(cursor: str) -> tuple[datetime, UUID]:
    if not cursor or len(cursor) > 256 or not cursor.isascii():
        raise InvalidTaskCursor

    try:
        padded = cursor + ("=" * (-len(cursor) % 4))
        decoded = base64.b64decode(
            padded.encode("ascii"),
            altchars=b"-_",
            validate=True,
        ).decode("utf-8")
        timestamp, raw_id = decoded.rsplit("|", 1)
        created_at = datetime.fromisoformat(timestamp)
        task_id = UUID(raw_id)
        if created_at.tzinfo is None:
            raise ValueError("Cursor timestamp has no timezone.")
        return created_at, task_id
    except (ValueError, UnicodeDecodeError) as error:
        raise InvalidTaskCursor from error
